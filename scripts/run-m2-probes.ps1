$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $repo
foreach ($probe in @('final-failure', 'interrupt')) {
    $log = Join-Path $repo ('outputs/m2-receipts/probe-' + $probe + '.log')
    $output = Join-Path $repo ('outputs/m2-probe-' + $probe)
    $arguments = @('-Xmx2g', '-cp', 'target/test-classes;target/spring-migration-agent.jar', 'dev.springmig.M2Probe', $probe, $repo, $output)
    $started = [DateTime]::UtcNow
    & java @arguments *> $log
    $exit = $LASTEXITCODE
    $receipt = [ordered]@{command = @('java') + $arguments; cwd = $repo; exitCode = $exit; startedAt = $started.ToString('o'); endedAt = [DateTime]::UtcNow.ToString('o'); log = $log; label = 'real Maven verification, explicit host fault/interruption injection'}
    [IO.File]::WriteAllText((Join-Path $repo ('outputs/m2-receipts/probe-' + $probe + '.json')), ($receipt | ConvertTo-Json -Depth 10))
    Get-Content -LiteralPath $log
    if ($exit -ne 0) { throw "Probe failed: $probe exit $exit" }
}
