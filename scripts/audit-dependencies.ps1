param([string]$Repo = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$taskRoot = [IO.Path]::GetFullPath($Repo)
$taskCache = Join-Path $taskRoot 'work\m2'
$taskOutput = Join-Path $taskRoot 'outputs\dependency-audit'
New-Item -ItemType Directory -Force -Path $taskOutput | Out-Null

function Read-PomLicenses([string]$taskPomPath, [string[]]$taskTrail = @()) {
    if (-not (Test-Path -LiteralPath $taskPomPath)) { return @{ licenses=@(); sources=$taskTrail; error='POM missing' } }
    if ($taskTrail -contains $taskPomPath) { return @{ licenses=@(); sources=$taskTrail; error='Parent cycle' } }
    $taskTrail += $taskPomPath
    # Cached POMs are upstream data; disable DTD/external resolution.
    $taskReaderSettings = [Xml.XmlReaderSettings]::new()
    $taskReaderSettings.DtdProcessing = [Xml.DtdProcessing]::Prohibit
    $taskReaderSettings.XmlResolver = $null
    $taskReader = [Xml.XmlReader]::Create($taskPomPath, $taskReaderSettings)
    $taskXml = [Xml.XmlDocument]::new()
    try { $taskXml.Load($taskReader) } finally { $taskReader.Dispose() }
    $taskLicenses = @($taskXml.project.licenses.license | Where-Object { $null -ne $_ -and $_.name } | ForEach-Object { [pscustomobject]@{name=[string]$_.name;url=[string]$_.url} })
    if ($taskLicenses.Count -gt 0) { return @{ licenses=$taskLicenses; sources=$taskTrail; error=$null } }
    $taskParent = $taskXml.project.parent
    if ($null -eq $taskParent) { return @{ licenses=@(); sources=$taskTrail; error='No license declaration in parent chain' } }
    $taskVersion = [string]$taskParent.version
    if ($taskVersion.Contains('$')) { return @{ licenses=@(); sources=$taskTrail; error='Unresolved parent version' } }
    $taskParentPom = Join-Path $taskCache (([string]$taskParent.groupId).Replace('.', '\')+'\'+[string]$taskParent.artifactId+'\'+$taskVersion+'\'+[string]$taskParent.artifactId+'-'+$taskVersion+'.pom')
    return Read-PomLicenses $taskParentPom $taskTrail
}

$taskRows = foreach ($taskJar in Get-ChildItem -Recurse -LiteralPath $taskCache -Filter '*.jar') {
    $taskRelative = $taskJar.FullName.Substring($taskCache.Length+1)
    $taskParts = $taskRelative.Split('\')
    $taskArtifact = $taskParts[-3]
    $taskVersion = $taskParts[-2]
    $taskGroup = ($taskParts[0..($taskParts.Length-4)]) -join '.'
    $taskPomPath = Join-Path $taskJar.DirectoryName ($taskArtifact+'-'+$taskVersion+'.pom')
    $taskAudit = Read-PomLicenses $taskPomPath
    [pscustomobject]@{
        coordinate=$taskGroup+':'+$taskArtifact+':'+$taskVersion
        file=$taskRelative
        sha256=(Get-FileHash -LiteralPath $taskJar.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        bytes=$taskJar.Length
        licenses=@($taskAudit.licenses)
        license_sources=@($taskAudit.sources | ForEach-Object { 'https://repo.maven.apache.org/maven2/'+$_.Substring($taskCache.Length+1).Replace('\','/') })
        review_error=$taskAudit.error
    }
}
$taskRows | Sort-Object coordinate,file | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $taskOutput 'resolved-artifacts.json') -Encoding utf8
$taskUnresolved = @($taskRows | Where-Object { $_.review_error })
$taskSummary = [pscustomobject]@{
    schema='springmig.dependency-audit/v1'
    recorded_at_utc=[DateTime]::UtcNow.ToString('o')
    scope='All JARs actually present in the isolated Maven cache, including build/tool/test artifacts; no statement that all are shipped in the CLI.'
    artifacts=$taskRows.Count
    total_jar_bytes=($taskRows | Measure-Object bytes -Sum).Sum
    unresolved_license_declarations=$taskUnresolved.Count
    unresolved_coordinates=@($taskUnresolved | ForEach-Object { $_.coordinate })
    source_available_recipe_artifacts=@($taskRows | Where-Object { $_.coordinate -match 'org.openrewrite.recipe:rewrite-spring' -or ($_.licenses.name -join ',') -match 'Moderne' } | Select-Object -ExpandProperty coordinate)
    limitation='Declared/inherited upstream license audit; not legal advice, binary-source equivalence proof, or a vulnerability audit. External cache is not distributed. Shaded runtime and wrapper notices are separately retained.'
}
$taskSummary | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath (Join-Path $taskOutput 'summary.json') -Encoding utf8
$taskSummary | ConvertTo-Json -Depth 6
if ($taskUnresolved.Count -gt 0) { exit 2 }
