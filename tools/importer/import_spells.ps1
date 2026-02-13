param(
    [Parameter(Mandatory = $true)]
    [string]$InputDir,

    [Parameter(Mandatory = $true)]
    [string]$OutputDir,

    [Parameter(Mandatory = $true)]
    [string]$SourceCommit
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $InputDir)) {
    throw "Input directory not found: $InputDir"
}

if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$allowedLicenses = @("ORC Notice", "OGL")
$spellFiles = Get-ChildItem -LiteralPath $InputDir -Filter *.json -File -Recurse

if ($spellFiles.Count -eq 0) {
    throw "No spell JSON files found in $InputDir"
}

$spells = @()
$licenseErrors = @()
$duplicateIdErrors = @()
$parseErrors = @()
$seenIds = New-Object System.Collections.Generic.HashSet[string]

foreach ($file in $spellFiles) {
    $json = $null
    try {
        $raw = Get-Content -LiteralPath $file.FullName -Raw
        $json = $raw | ConvertFrom-Json -Depth 100
    }
    catch {
        $parseErrors += [PSCustomObject]@{
            file = $file.FullName
            error = $_.Exception.Message
        }
        continue
    }

    if ($null -ne $json.type -and $json.type -ne "spell") {
        continue
    }

    $spellId = [System.IO.Path]::GetFileNameWithoutExtension($file.Name)
    if (-not $seenIds.Add($spellId)) {
        $duplicateIdErrors += [PSCustomObject]@{
            file = $file.FullName
            id = $spellId
        }
        continue
    }

    $license = $null
    $candidateLicenses = @(
        $json.system.publication.license,
        $json.system.license,
        $json._stats.license,
        $json.system.source.license
    )

    foreach ($candidate in $candidateLicenses) {
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            $license = $candidate
            break
        }
    }

    if ([string]::IsNullOrWhiteSpace($license) -or ($allowedLicenses -notcontains $license)) {
        $licenseErrors += [PSCustomObject]@{
            file = $file.Name
            license = $license
        }
        continue
    }

    $level = 0
    if ($null -ne $json.system.level.value) {
        $level = [int]$json.system.level.value
    }

    $traits = @()
    if ($null -ne $json.system.traits.value) {
        $traits = @($json.system.traits.value)
    }

    $traditions = @()
    if ($null -ne $json.system.traits.traditions) {
        $traditions = @($json.system.traits.traditions)
    }

    $publication = $json.system.publication

    $spells += [PSCustomObject]@{
        id = $spellId
        name = $json.name
        rank = $level
        rarity = $json.system.traits.rarity
        traits = $traits
        traditions = $traditions
        cast = $json.system.time.value
        range = $json.system.range.value
        target = $json.system.target.value
        area = $json.system.area.value
        duration = $json.system.duration.value
        save = $json.system.defense.save.basic
        source = [PSCustomObject]@{
            book = $publication.title
            page = $publication.page
        }
        license = $license
        description = $json.system.description.value
    }
}

if ($parseErrors.Count -gt 0) {
    $parseErrorFile = Join-Path $OutputDir "parse-errors.json"
    $parseErrors | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $parseErrorFile -Encoding UTF8
    throw "JSON parse errors detected in $($parseErrors.Count) files. See $parseErrorFile"
}

if ($duplicateIdErrors.Count -gt 0) {
    $duplicateErrorFile = Join-Path $OutputDir "duplicate-id-errors.json"
    $duplicateIdErrors | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $duplicateErrorFile -Encoding UTF8
    throw "Duplicate spell IDs detected in $($duplicateIdErrors.Count) files. See $duplicateErrorFile"
}

if ($licenseErrors.Count -gt 0) {
    $licenseErrorFile = Join-Path $OutputDir "license-errors.json"
    $licenseErrors | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $licenseErrorFile -Encoding UTF8
    throw "License validation failed for $($licenseErrors.Count) files. See $licenseErrorFile"
}

if ($spells.Count -eq 0) {
    throw "No valid spell entries produced by importer."
}

$spells = $spells | Sort-Object -Property rank, name

$datasetVersion = Get-Date -Format "yyyyMMdd-HHmmss"

$normalizedPath = Join-Path $OutputDir "spells.normalized.json"
$attributionPath = Join-Path $OutputDir "spells.attribution.json"
$changelogPath = Join-Path $OutputDir "spells.changelog.json"

$normalized = [PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    spellCount = $spells.Count
    spells = $spells
}

$attributions = $spells |
    Group-Object -Property license |
    ForEach-Object {
        [PSCustomObject]@{
            license = $_.Name
            count = $_.Count
            sources = ($_.Group.source.book | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)
        }
    }

$attribution = [PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    entries = $attributions
}

# Phase 0 changelog is intentionally coarse; later phases compare against prior dataset snapshots.
$changelog = [PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    added = $spells.Count
    changed = 0
    removed = 0
}

$normalized | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $normalizedPath -Encoding UTF8
$attribution | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $attributionPath -Encoding UTF8
$changelog | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $changelogPath -Encoding UTF8

Write-Host "Generated dataset:"
Write-Host "  - $normalizedPath"
Write-Host "  - $attributionPath"
Write-Host "  - $changelogPath"
