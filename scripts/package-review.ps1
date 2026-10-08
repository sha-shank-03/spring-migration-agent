param([string]$Out = ('outputs/package-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0,8)))
$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'review-helpers.ps1')
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location -LiteralPath $repo
$root = [IO.Path]::GetFullPath($Out)
Assert-True ($root.StartsWith((Join-Path $repo 'outputs') + [IO.Path]::DirectorySeparatorChar) -and -not (Test-Path -LiteralPath $root)) 'Use a new directory inside repository outputs/'
Assert-True (Test-Path -LiteralPath target/spring-migration-agent.jar) 'Build the CLI before packaging'
$staging = Join-Path $root 'files'
New-Item -ItemType Directory -Path $staging | Out-Null
$entries = @()
$paths = @('.github','.mvn','src','examples','verification','recipes','scripts','docs','third-party','.gitignore','AGENTS.md','pom.xml','mvnw','mvnw.cmd','README.md','PLAN.md','EVIDENCE.md','DEPENDENCIES.md','LICENSE','NOTICE')
foreach ($relative in $paths) {
    $source = Join-Path $repo $relative
    Assert-True (Test-Path -LiteralPath $source) ('Missing package source: ' + $relative)
    $files = if ((Get-Item -LiteralPath $source).PSIsContainer) { @(Get-ChildItem -LiteralPath $source -File -Recurse -Force) } else { @(Get-Item -LiteralPath $source) }
    foreach ($file in $files) {
        $path = $file.FullName.Substring($repo.Length + 1).Replace('\','/')
        Assert-True (-not ($path -match '(^|/)(target|work|outputs|\.git)(/|$)')) ('Generated/cache path in package: ' + $path)
        $destination = Join-Path $staging $path
        New-Item -ItemType Directory -Path (Split-Path -Parent $destination) -Force | Out-Null
        Copy-Item -LiteralPath $file.FullName -Destination $destination
        $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $file.FullName).Hash.ToLowerInvariant()
        Assert-True ($hash -eq (Get-FileHash -Algorithm SHA256 -LiteralPath $destination).Hash.ToLowerInvariant()) 'Staged source bytes differ'
        $entries += [ordered]@{path=$path;sha256=$hash;bytes=$file.Length;kind='source'}
    }
}
$bin = Join-Path $staging 'bin'
New-Item -ItemType Directory -Path $bin | Out-Null
Copy-Item -LiteralPath target/spring-migration-agent.jar -Destination $bin
$jar = Get-Item -LiteralPath target/spring-migration-agent.jar
$entries += [ordered]@{path='bin/spring-migration-agent.jar';sha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $jar.FullName).Hash.ToLowerInvariant();bytes=$jar.Length;kind='built CLI; Gson/Error Prone attribution accompanies it'}
$manifest = [ordered]@{schema='springmig.review-package/v1';createdAt=[DateTime]::UtcNow.ToString('o');publication='local review only; no release or push';excluded=@('Maven distributions/cache','generated Spring application JARs','Git metadata','large raw run workspaces');files=@($entries | Sort-Object path)}
Write-ReviewJson (Join-Path $staging 'manifest.json') $manifest
Write-ReviewJson (Join-Path $root 'source-and-cli-manifest.json') $manifest
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = Join-Path $root 'spring-migration-agent-review.zip'
[IO.Compression.ZipFile]::CreateFromDirectory($staging,$zip,[IO.Compression.CompressionLevel]::Optimal,$false)
$archive = [IO.Compression.ZipFile]::OpenRead($zip)
try {
    Assert-True ($archive.Entries.Count -eq $entries.Count + 1) 'ZIP entry count differs'
    foreach ($item in $entries) {
        $entry = $archive.GetEntry($item.path)
        Assert-True ($null -ne $entry) ('ZIP entry missing: ' + $item.path)
        $stream = $entry.Open(); $sha = [Security.Cryptography.SHA256]::Create()
        try { $hash = ([BitConverter]::ToString($sha.ComputeHash($stream))).Replace('-','').ToLowerInvariant() }
        finally { $stream.Dispose();$sha.Dispose() }
        Assert-True ($hash -eq $item.sha256) ('ZIP bytes differ: ' + $item.path)
    }
} finally { $archive.Dispose() }
Write-ReviewJson (Join-Path $root 'package-receipt.json') ([ordered]@{schema='springmig.package-receipt/v1';zip=$zip;sha256=(Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash.ToLowerInvariant();bytes=(Get-Item -LiteralPath $zip).Length;sourceFiles=@($entries | Where-Object kind -eq 'source').Count;allZipEntryHashesPassed=$true;distributedCliDependencies=@('Gson 2.13.2','Error Prone annotations 2.41.0');hostedCIExecuted=$false;published=$false})
Write-Host ('Local review package verified: ' + $zip)
