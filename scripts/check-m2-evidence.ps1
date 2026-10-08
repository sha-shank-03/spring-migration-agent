$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $repo
function Assert-True([bool]$Value, [string]$Reason) { if (-not $Value) { throw $Reason } }
function Assert-Manifest($Manifest, [string]$Root) {
    foreach ($entry in $Manifest.PSObject.Properties) {
        $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $Root $entry.Name)).Hash.ToLowerInvariant()
        Assert-True ($actual -eq $entry.Value) ('Manifest mismatch: ' + $Root + '/' + $entry.Name)
    }
}
function Get-TreeDigest([string]$Root) {
    $hashes = New-Object 'System.Collections.Generic.SortedDictionary[string,string]' ([StringComparer]::Ordinal)
    foreach ($file in Get-ChildItem -LiteralPath $Root -File -Recurse -Force) {
        $relative = $file.FullName.Substring($Root.Length + 1).Replace('\', '/')
        if ($relative -match '(^|/)(\.git|target)(/|$)') { continue }
        $hashes.Add($relative, (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant())
    }
    $builder = New-Object Text.StringBuilder
    foreach ($entry in $hashes.GetEnumerator()) { [void]$builder.Append($entry.Key).Append([char]0).Append($entry.Value).Append("`n") }
    $sha = [Security.Cryptography.SHA256]::Create()
    try { return ([BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($builder.ToString())))).Replace('-', '').ToLowerInvariant() }
    finally { $sha.Dispose() }
}
$directories = @('m2-final-three-mode-offline/recipes-only', 'm2-final-three-mode-offline/agent-only', 'm2-final-three-mode-offline/hybrid', 'm2 replay offline', 'm2-mechanical-offline/recipes-only', 'm2-mechanical-offline/agent-only', 'm2-mechanical-offline/hybrid', 'm2-unresolved-offline', 'm2-missing-cache', 'm2-run-deadline', 'm2-command-timeout', 'm2-proposal-budget', 'm2-action-budget', 'm2-replay-mismatch', 'm2-probe-final-failure', 'm2-probe-interrupt')
$summary = @()
foreach ($directory in $directories) {
    $root = Join-Path $repo ('outputs/' + $directory)
    $report = Get-Content -LiteralPath (Join-Path $root 'report.json') -Raw | ConvertFrom-Json
    Assert-True ($report.originalInputIntact -and $report.originalContractIntact -and $report.protectedVerificationIntact) ('Integrity false: ' + $directory)
    Assert-True ($report.offline -and $report.liveModelCalls -eq 0) ('Offline/mock label failure: ' + $directory)
    Assert-Manifest $report.sourceManifest (Join-Path $root 'baseline')
    Assert-Manifest $report.sourceManifest $report.input
    Assert-Manifest $report.protectedManifest (Join-Path $root 'protected')
    Assert-True ((Get-TreeDigest (Join-Path $root 'candidate')) -eq $report.finalCandidateSha256) ('Final candidate digest mismatch: ' + $directory)
    if ($directory.StartsWith('m2-final-three-mode-offline/')) { Assert-Manifest $report.sourceManifest (Join-Path $repo 'examples/mvc-validation') }
    if ($directory.StartsWith('m2-mechanical-offline/')) { Assert-Manifest $report.sourceManifest (Join-Path $repo 'examples/mechanical-only') }
    $maven = @($report.commands | Where-Object { $_.argv -contains 'org.codehaus.plexus.classworlds.launcher.Launcher' })
    foreach ($stage in $report.stages) {
        $maven += $stage.build
        Assert-True ($stage.appCleanupComplete -and $stage.protectedIntegrity -and $stage.build.cleanupComplete) ('Stage cleanup/integrity failure: ' + $directory + '/' + $stage.stage)
        if ($stage.buildPassed) { Assert-True ($stage.testsRun -eq 3) ('Original test count changed: ' + $directory) }
    }
    if ($report.interruptedProcess) { $maven += $report.interruptedProcess }
    foreach ($command in $maven) {
        Assert-True ($command.argv -contains '-o') ('Missing actual Maven -o: ' + $directory)
        Assert-True ($command.argv -contains '-Xmx2g') ('Missing tool JVM cap: ' + $directory)
        Assert-True $command.cleanupComplete ('Child cleanup false: ' + $directory)
        if ($command.argv -contains 'verify') { Assert-True ($command.argv -contains '-DargLine=-Xmx2g') ('Missing test fork heap cap: ' + $directory) }
    }
    $workspaces = @($report.stages | ForEach-Object { $_.workspace })
    Assert-True (@($workspaces | Select-Object -Unique).Count -eq $workspaces.Count) ('Verification workspace reused: ' + $directory)
    if ($report.status -eq 'verified') { $last = $report.stages[-1]; Assert-True ($last.stage -eq 'final' -and $last.passed) ('Missing fresh final verification: ' + $directory) }
    $summary += [ordered]@{ output = $directory; status = $report.status; exitCode = $report.exitCode; agentKind = $report.agentKind; recipeExecuted = $report.recipeExecuted; proposals = @($report.repairProposals).Count; toolActions = $report.toolActions; stages = @($report.stages).Count; actualOfflineMavenCommands = $maven.Count; integrityAndCleanupPassed = $true }
}
$comparison = Get-Content outputs/m2-final-three-mode-offline/comparison.json -Raw | ConvertFrom-Json
Assert-True ($comparison.identicalSnapshots -and $comparison.expectedFixtureOutcome) 'Three-mode comparison failed'
Assert-True ($comparison.modes.'recipes-only'.exitCode -eq 2 -and $comparison.modes.'agent-only'.exitCode -eq 0 -and $comparison.modes.hybrid.exitCode -eq 0) 'Unexpected main matrix'
$mock = Get-Content outputs/m2-final-three-mode-offline/hybrid/report.json -Raw | ConvertFrom-Json
$replay = Get-Content -LiteralPath 'outputs/m2 replay offline/report.json' -Raw | ConvertFrom-Json
Assert-True ($replay.agentKind -eq 'recorded_mock_replay' -and $mock.finalCandidateSha256 -eq $replay.finalCandidateSha256) 'Replay candidate mismatch'
$mechanical = Get-Content outputs/m2-mechanical-offline/comparison.json -Raw | ConvertFrom-Json
Assert-True ($mechanical.identicalSnapshots -and $mechanical.expectedFixtureOutcome) 'Mechanical comparison failed'
$unresolved = Get-Content outputs/m2-unresolved-offline/report.json -Raw | ConvertFrom-Json
Assert-True ($unresolved.repairStopReason -eq 'unsupported_diagnostics' -and $unresolved.exitCode -eq 2 -and $unresolved.stages[-1].stage -eq 'final') 'Unresolved repair did not stop/finalize'
$owned = @(Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -like ('*' + $repo + '*') -or $_.CommandLine -like '*spring-migration-agent.jar*' })
Assert-True ($owned.Count -eq 0) 'Owned JVM still running'
$result = [ordered]@{ schema = 'springmig.m2-evidence-check/v1'; observedAt = [DateTime]::UtcNow.ToString('o'); passed = $true; reports = $summary; mainMatrixSnapshotSha256 = $comparison.snapshotSha256; replayMatchesFreshMockCandidate = $true; actualOfflineFlagsAndHeapCapsPassed = $true; originalAndProtectedManifestsPassed = $true; distinctVerificationWorkspacesPassed = $true; ownedJvmCount = $owned.Count; freeGiB = [Math]::Round((Get-PSDrive C).Free / 1GB, 2) }
[IO.File]::WriteAllText((Join-Path $repo 'outputs/m2-evidence-summary.json'), ($result | ConvertTo-Json -Depth 30))
$result | ConvertTo-Json -Depth 30
