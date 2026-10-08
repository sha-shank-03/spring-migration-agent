param([string]$Phase = 'prepare')
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $repo
$receipts = Join-Path $repo 'outputs/m2-receipts'
New-Item -ItemType Directory -Path $receipts -Force | Out-Null
function Invoke-Case([string]$Name, [string[]]$Arguments, [int]$Expected) {
    $log = Join-Path $receipts ($Name + '.log')
    $started = [DateTime]::UtcNow
    & java -Xmx2g -jar target/spring-migration-agent.jar @Arguments *> $log
    $exit = $LASTEXITCODE
    $receipt = [ordered]@{ command = @('java', '-Xmx2g', '-jar', 'target/spring-migration-agent.jar') + $Arguments; cwd = $repo; exitCode = $exit; expectedExit = $Expected; passed = $exit -eq $Expected; startedAt = $started.ToString('o'); endedAt = [DateTime]::UtcNow.ToString('o'); log = $log }
    [IO.File]::WriteAllText((Join-Path $receipts ($Name + '.json')), ($receipt | ConvertTo-Json -Depth 10))
    Get-Content -LiteralPath $log
    if ($exit -ne $Expected) { throw "$Name expected $Expected but observed $exit" }
}
switch ($Phase) {
    'remaining' {
        & $PSCommandPath -Phase replay
        & $PSCommandPath -Phase fixtures
        & $PSCommandPath -Phase failures
        & (Join-Path $PSScriptRoot 'run-m2-probes.ps1')
    }
    'prepare' { Invoke-Case 'prepare-cache' @('prepare-cache', '--out', 'outputs/m2-prepare-cache') 0 }
    'compare' { Invoke-Case 'final-three-mode-offline' @('compare', '--agent', 'mock', '--offline', '--out', 'outputs/m2-final-three-mode-offline') 0 }
    'replay' { Invoke-Case 'recorded-replay-offline' @('demo', '--mode', 'hybrid', '--agent', 'replay', '--replay', 'outputs/m2-final-three-mode-offline/hybrid/mock-transcript.json', '--offline', '--out', 'outputs/m2 replay offline') 0 }
    'fixtures' {
        Invoke-Case 'mechanical-offline' @('compare', '--fixture', 'mechanical-only', '--offline', '--out', 'outputs/m2-mechanical-offline') 0
        Invoke-Case 'unresolved-offline' @('demo', '--fixture', 'unresolved-repair', '--mode', 'hybrid', '--offline', '--out', 'outputs/m2-unresolved-offline') 2
    }
    'failures' {
        Invoke-Case 'missing-cache' @('demo', '--offline', '--cache', 'work/empty-m2-cache', '--out', 'outputs/m2-missing-cache') 4
        Invoke-Case 'run-deadline' @('demo', '--offline', '--run-seconds', '1', '--out', 'outputs/m2-run-deadline') 5
        Invoke-Case 'command-timeout' @('demo', '--offline', '--command-seconds', '1', '--out', 'outputs/m2-command-timeout') 4
        Invoke-Case 'proposal-budget' @('demo', '--mode', 'agent-only', '--offline', '--max-proposals', '1', '--out', 'outputs/m2-proposal-budget') 5
        Invoke-Case 'action-budget' @('demo', '--mode', 'hybrid', '--offline', '--max-actions', '1', '--out', 'outputs/m2-action-budget') 5
        # The mvc transcript cannot match the mechanical fixture or its successful diagnostics.
        # Use a changed fixture source retaining the failing route, so a request actually occurs.
        $copy = Join-Path $repo 'work/replay-changed-input'
        New-Item -ItemType Directory -Path $copy | Out-Null
        Copy-Item -LiteralPath examples/mvc-validation/pom.xml -Destination $copy
        Copy-Item -LiteralPath examples/mvc-validation/src -Destination $copy -Recurse
        $source = Join-Path $copy 'src/main/java/demo/Application.java'
        [IO.File]::AppendAllText($source, "`n// Deliberately different source for replay rejection.`n")
        Invoke-Case 'replay-mismatch' @('migrate', '--input', $copy, '--contract', 'verification/mvc-validation/contract.json', '--mode', 'hybrid', '--agent', 'replay', '--replay', 'outputs/m2-final-three-mode-offline/hybrid/mock-transcript.json', '--offline', '--out', 'outputs/m2-replay-mismatch') 6
    }
    default { throw 'Use prepare, compare, replay, fixtures, failures or remaining' }
}
