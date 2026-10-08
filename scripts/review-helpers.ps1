# Host review assertions; no editing-agent authority and no runtime dependencies.
function Assert-True([bool]$Value, [string]$Reason) { if (-not $Value) { throw $Reason } }
function Write-ReviewJson([string]$Path, $Value) { [IO.File]::WriteAllText($Path, ($Value | ConvertTo-Json -Depth 60)) }
function Get-TreeDigest([string]$Root) {
    $Root = [IO.Path]::GetFullPath($Root).TrimEnd([IO.Path]::DirectorySeparatorChar)
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
function Assert-Manifest($Manifest, [string]$Root) {
    Assert-True ($null -ne $Manifest) ('Missing manifest: ' + $Root)
    foreach ($entry in $Manifest.PSObject.Properties) {
        $actual = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $Root $entry.Name)).Hash.ToLowerInvariant()
        Assert-True ($actual -eq $entry.Value) ('Manifest mismatch: ' + $Root + '/' + $entry.Name)
    }
}
function Get-OwnedJava([string]$Repo) {
    if ([Environment]::OSVersion.Platform -eq [PlatformID]::Win32NT) {
        $all = @(Get-CimInstance Win32_Process -Filter "Name='java.exe' OR Name='javaw.exe'")
        Assert-True (@($all | Where-Object { -not $_.CommandLine }).Count -eq 0) 'Java command line unreadable; cannot establish cleanup'
        return @($all | Where-Object { $_.CommandLine.Contains($Repo) -or $_.CommandLine.Contains('spring-migration-agent.jar') })
    }
    $owned = @()
    foreach ($line in (& ps -eo 'pid=,comm=,args=')) {
        if ($line -match '^\s*(\d+)\s+java\s+(.+)$') {
            if ($line.Contains($Repo) -or $line.Contains('spring-migration-agent.jar')) { $owned += $line }
        }
    }
    Assert-True ($LASTEXITCODE -eq 0) 'ps failed; cannot establish cleanup'
    return $owned
}
function Assert-ReviewReport([string]$Path, [string]$Status, [int]$Exit, [string]$Kind, [bool]$Recipe, [bool]$Offline = $true) {
    $report = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    $root = Split-Path -Parent $Path
    Assert-True ($report.schema -eq 'springmig.report/v2' -and $report.target -eq 'boot-2.7.18-to-3.0.13') 'Report schema/target differs'
    Assert-True ($report.status -eq $Status -and $report.exitCode -eq $Exit) ('Wrong status/exit: ' + $Path)
    Assert-True ($report.agentKind -eq $Kind -and $report.recipeExecuted -eq $Recipe -and $report.liveModelCalls -eq 0) ('Mock/recipe labels differ: ' + $Path)
    Assert-True ($report.offline -eq $Offline -and $report.historicalTargetUnsupported) 'Offline/historical flag differs'
    Assert-True ($report.originalInputIntact -and $report.originalContractIntact -and $report.protectedVerificationIntact) ('Integrity failure: ' + $Path)
    Assert-Manifest $report.sourceManifest (Join-Path $root 'baseline')
    Assert-Manifest $report.sourceManifest $report.input
    Assert-Manifest $report.protectedManifest (Join-Path $root 'protected')
    $contractPath = Join-Path $root 'protected/contract.json'
    $contract = Get-Content -LiteralPath $contractPath -Raw | ConvertFrom-Json
    Assert-True ($contract.schema -eq 'springmig.http-contract/v1' -and (Get-FileHash -LiteralPath $contractPath -Algorithm SHA256).Hash.ToLowerInvariant() -eq $report.contractSha256) 'Protected contract provenance differs'
    Assert-True ((Get-TreeDigest (Join-Path $root 'baseline')) -eq $report.snapshotSha256) 'Baseline digest differs'
    Assert-True ((Get-TreeDigest (Join-Path $root 'candidate')) -eq $report.finalCandidateSha256) 'Candidate digest differs'
    Assert-True ($report.toolActions -le $report.budgets.toolActions -and @($report.repairProposals).Count -le $report.budgets.repairProposals) 'Recorded budget exceeded'
    $maven = @($report.commands | Where-Object { $_.argv -contains 'org.codehaus.plexus.classworlds.launcher.Launcher' })
    foreach ($command in $report.commands) { Assert-True $command.cleanupComplete 'Command cleanup incomplete' }
    $workspaces = @()
    foreach ($stage in $report.stages) {
        $workspaces += $stage.workspace
        $maven += $stage.build
        Assert-True ($stage.protectedIntegrity -and $stage.build.cleanupComplete -and $stage.appCleanupComplete) 'Stage integrity/cleanup incomplete'
        Assert-True ($stage.build.argv -contains 'clean' -and $stage.build.argv -contains '-DskipTests=false' -and $stage.build.argv -contains '-Dmaven.test.skip=false' -and $stage.build.argv -contains '-DargLine=-Xmx2g') 'Host verification flags differ'
        if ($stage.buildPassed) {
            Assert-True ($stage.build.exitCode -eq 0 -and -not $stage.build.timedOut -and $stage.testsRun -eq 3) 'Successful fixture stage did not run three tests'
            $tests = 0
            foreach ($xmlPath in Get-ChildItem -LiteralPath (Join-Path $stage.workspace 'target/surefire-reports') -Filter 'TEST-*.xml') {
                # Read trusted generated XML without external DTD resolution.
                $settings = New-Object Xml.XmlReaderSettings
                $settings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
                $settings.XmlResolver = $null
                $reader = [Xml.XmlReader]::Create($xmlPath.FullName, $settings)
                try { $xml = New-Object Xml.XmlDocument; $xml.XmlResolver = $null; $xml.Load($reader) } finally { $reader.Dispose() }
                Assert-True ([int]$xml.testsuite.failures -eq 0 -and [int]$xml.testsuite.errors -eq 0) 'Surefire XML failed'
                $tests += [int]$xml.testsuite.tests - [int]$xml.testsuite.skipped
            }
            Assert-True ($tests -eq 3) 'Physical Surefire XML count differs'
        }
        foreach ($check in $stage.checks) {
            $expected = @($contract.checks | Where-Object { $_.id -ceq $check.id })
            Assert-True ($expected.Count -eq 1 -and $expected[0].expectedStatus -eq $check.expectedStatus -and $expected[0].expectedBody -ceq $check.expectedBody) ('Observation expectation differs from protected contract: ' + $check.id)
            $passed = $null -eq $check.error -and $check.actualStatus -eq $check.expectedStatus -and ($null -eq $check.expectedBody -or $check.actualBody -ceq $check.expectedBody)
            Assert-True ($passed -eq $check.passed) ('Literal HTTP result differs: ' + $check.id)
        }
        if ($stage.buildPassed -and $null -eq $stage.error) { Assert-True (@($stage.checks).Count -eq @($contract.checks).Count) 'HTTP contract check omitted' }
    }
    if ($report.interruptedProcess) { $maven += $report.interruptedProcess }
    foreach ($command in $maven) {
        Assert-True ($command.argv -contains '-Xmx2g' -and $command.cleanupComplete) 'Maven heap/cleanup differs'
        Assert-True (($command.argv -contains '-o') -eq $Offline) 'Actual Maven offline flag differs'
    }
    Assert-True (@($workspaces | Select-Object -Unique).Count -eq $workspaces.Count) 'Verification workspace reused'
    if ($Status -eq 'verified') {
        $last = $report.stages[-1]
        Assert-True ($last.stage -eq 'final' -and $last.passed -and @($last.checks).Count -eq 6 -and @($last.checks | Where-Object { -not $_.passed }).Count -eq 0) 'Missing successful fresh final HTTP verification'
    }
    return $report
}
function Assert-ReviewMatrix([string]$Root, [bool]$Mechanical = $false) {
    $comparison = Get-Content -LiteralPath (Join-Path $Root 'comparison.json') -Raw | ConvertFrom-Json
    Assert-True ($comparison.schema -eq 'springmig.comparison/v2' -and $comparison.evaluation -eq 'scripted_mock_harness' -and $comparison.identicalSnapshots -and $comparison.expectedFixtureOutcome) 'Comparison assertions failed'
    foreach ($mode in @('recipes-only', 'agent-only', 'hybrid')) {
        $exit = if ($mode -eq 'recipes-only' -and -not $Mechanical) { 2 } else { 0 }
        $status = if ($exit -eq 0) { 'verified' } else { 'verification_failed' }
        $kind = if ($mode -eq 'recipes-only') { 'none' } else { 'scripted_mock' }
        $report = Assert-ReviewReport (Join-Path $Root ($mode + '/report.json')) $status $exit $kind ($mode -ne 'agent-only')
        Assert-True ($report.snapshotSha256 -eq $comparison.snapshotSha256 -and $comparison.modes.$mode.exitCode -eq $exit) 'Literal mode exit/snapshot differs'
        if ($exit -eq 2) {
            $last = $report.stages[-1]
            Assert-True ($last.stage -eq 'final' -and $last.buildPassed -and @($last.checks | Where-Object passed).Count -eq 5 -and @($last.checks).Count -eq 6) 'Expected recipes-only HTTP regression differs'
        }
    }
    return $comparison
}
