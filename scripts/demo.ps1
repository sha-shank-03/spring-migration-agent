param([string]$Out = ('outputs/review-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)), [switch]$Offline, [switch]$Extended)
$ErrorActionPreference = 'Stop'
# Expected nonzero native exits are explicitly asserted below, also on PowerShell 7.
if (Test-Path variable:PSNativeCommandUseErrorActionPreference) { $PSNativeCommandUseErrorActionPreference = $false }
. (Join-Path $PSScriptRoot 'review-helpers.ps1')
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $repo
$root = [IO.Path]::GetFullPath($Out)
Assert-True (-not (Test-Path -LiteralPath $root)) 'Use a new output directory; prior evidence is preserved'
Assert-True ($root.StartsWith((Join-Path $repo 'outputs') + [IO.Path]::DirectorySeparatorChar)) 'Review output must be inside repository outputs/'
Assert-True (Test-Path -LiteralPath target/spring-migration-agent.jar) 'Build the CLI with mvnw clean verify first'
New-Item -ItemType Directory -Path $root | Out-Null
$receipts = @()
$original = Get-TreeDigest (Join-Path $repo 'examples/mvc-validation')
function Invoke-ReviewJava([string]$Name, [string[]]$Arguments, [int]$Expected) {
    $log = Join-Path $root ($Name + '.log')
    $started = [DateTime]::UtcNow
    & java -Xmx2g -jar target/spring-migration-agent.jar @Arguments *> $log
    $code = $LASTEXITCODE
    $receipt = [ordered]@{schema='springmig.review-command/v1';command=@('java','-Xmx2g','-jar','target/spring-migration-agent.jar')+$Arguments;cwd=$repo;exitCode=$code;expectedExit=$Expected;startedAt=$started.ToString('o');endedAt=[DateTime]::UtcNow.ToString('o');log=$log}
    Write-ReviewJson (Join-Path $root ($Name + '-command.json')) $receipt
    Get-Content -LiteralPath $log
    Assert-True ($code -eq $Expected) ($Name + ' expected ' + $Expected + ' but observed ' + $code)
}
if (-not $Offline) {
    Invoke-ReviewJava 'prepare' @('prepare-cache','--out',(Join-Path $root 'prepare')) 0
    $null = Assert-ReviewReport (Join-Path $root 'prepare/report.json') 'verified' 0 'scripted_mock' $true $false
}
Invoke-ReviewJava 'comparison' @('compare','--agent','mock','--offline','--out',(Join-Path $root 'comparison')) 0
$matrix = Assert-ReviewMatrix (Join-Path $root 'comparison')
Invoke-ReviewJava 'replay' @('demo','--mode','hybrid','--agent','replay','--replay',(Join-Path $root 'comparison/hybrid/mock-transcript.json'),'--offline','--out',(Join-Path $root 'replay with spaces')) 0
$replay = Assert-ReviewReport (Join-Path $root 'replay with spaces/report.json') 'verified' 0 'recorded_mock_replay' $true
$mock = Get-Content -LiteralPath (Join-Path $root 'comparison/hybrid/report.json') -Raw | ConvertFrom-Json
Assert-True ($mock.finalCandidateSha256 -eq $replay.finalCandidateSha256) 'Replay does not match fresh mock candidate'
$cache = Join-Path $repo ('work/runs/cache-miss-' + [Guid]::NewGuid().ToString('N'))
Assert-True (-not (Test-Path -LiteralPath $cache)) 'Cache-miss directory already exists'
Invoke-ReviewJava 'cache-miss' @('demo','--offline','--cache',$cache,'--out',(Join-Path $root 'cache-miss')) 4
$miss = Assert-ReviewReport (Join-Path $root 'cache-miss/report.json') 'offline_cache_miss' 4 'scripted_mock' $false
Assert-True ((Get-Content -LiteralPath $miss.stages[0].build.log -Raw).Contains('spring-boot-starter-parent:pom:2.7.18')) 'Missing coordinate is not the expected baseline parent'
Invoke-ReviewJava 'timeout' @('demo','--offline','--command-seconds','1','--out',(Join-Path $root 'timeout')) 4
$null = Assert-ReviewReport (Join-Path $root 'timeout/report.json') 'command_timeout' 4 'scripted_mock' $false
$changed = Join-Path $root 'changed-input'
New-Item -ItemType Directory -Path $changed | Out-Null
Copy-Item -LiteralPath examples/mvc-validation/pom.xml -Destination $changed
Copy-Item -LiteralPath examples/mvc-validation/src -Destination $changed -Recurse
[IO.File]::AppendAllText((Join-Path $changed 'src/main/java/demo/Application.java'), "`n// Review-only behavior-preserving source mismatch.`n")
Invoke-ReviewJava 'replay-mismatch' @('migrate','--input',$changed,'--contract',(Join-Path $repo 'verification/mvc-validation/contract.json'),'--mode','hybrid','--agent','replay','--replay',(Join-Path $root 'comparison/hybrid/mock-transcript.json'),'--offline','--out',(Join-Path $root 'replay-mismatch')) 6
$mismatch = Assert-ReviewReport (Join-Path $root 'replay-mismatch/report.json') 'policy_denied' 6 'recorded_mock_replay' $true
Assert-True ($mismatch.error.Contains('replay_request_source_mismatch') -and @($mismatch.repairProposals).Count -eq 0) 'Replay mismatch accepted a proposal or wrong error'
if ($Extended) {
    Invoke-ReviewJava 'mechanical' @('compare','--fixture','mechanical-only','--offline','--out',(Join-Path $root 'mechanical')) 0
    $null = Assert-ReviewMatrix (Join-Path $root 'mechanical') $true
    Invoke-ReviewJava 'unresolved' @('demo','--fixture','unresolved-repair','--offline','--out',(Join-Path $root 'unresolved')) 2
    $unresolved = Assert-ReviewReport (Join-Path $root 'unresolved/report.json') 'verification_failed' 2 'scripted_mock' $true
    Assert-True ($unresolved.repairStopReason -eq 'unsupported_diagnostics' -and @($unresolved.stages[-1].checks).Count -eq 7 -and @($unresolved.stages[-1].checks | Where-Object passed).Count -eq 5) 'Unresolved protected behavior differs'
}
$patchReview = Join-Path $root 'patch-review'
New-Item -ItemType Directory -Path $patchReview | Out-Null
Copy-Item -LiteralPath examples/mvc-validation/pom.xml -Destination $patchReview
Copy-Item -LiteralPath examples/mvc-validation/src -Destination $patchReview -Recurse
$patch = Join-Path $root 'comparison/hybrid/migration.patch'
$gitCommands = @(@('init','--quiet'),@('config','core.autocrlf','false'),@('apply','--check',$patch),@('apply',$patch))
foreach ($arguments in $gitCommands) { & git -C $patchReview @arguments; Assert-True ($LASTEXITCODE -eq 0) 'Fresh patch application failed' }
Assert-True ((Get-TreeDigest $patchReview) -eq $mock.finalCandidateSha256) 'Applied patch bytes differ from verified candidate'
Assert-True ((Get-TreeDigest (Join-Path $repo 'examples/mvc-validation')) -eq $original) 'Caller fixture changed'
$owned = @(Get-OwnedJava $repo)
Assert-True ($owned.Count -eq 0) 'Owned JVM still running'
Write-ReviewJson (Join-Path $root 'summary.json') ([ordered]@{schema='springmig.review-demo/v1';passed=$true;observedAt=[DateTime]::UtcNow.ToString('o');offlineOnly=[bool]$Offline;extended=[bool]$Extended;snapshotSha256=$matrix.snapshotSha256;modeExits=@(2,0,0);replayCandidateSha256=$replay.finalCandidateSha256;cacheMissExit=4;commandTimeoutExit=4;replayMismatchExit=6;patchMatchesCandidate=$true;originalFixtureIntact=$true;ownedJvmCount=$owned.Count;liveModelCalls=0})
Write-Host ('Review assertions passed: ' + (Join-Path $root 'summary.json'))
