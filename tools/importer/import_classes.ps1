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

function Get-NestedValue {
    param(
        [Parameter(Mandatory = $false)]
        [object]$Object,

        [Parameter(Mandatory = $true)]
        [string[]]$Path
    )

    $current = $Object
    foreach ($segment in $Path) {
        if ($null -eq $current) {
            return $null
        }

        $prop = $current.PSObject.Properties[$segment]
        if ($null -eq $prop) {
            return $null
        }

        $current = $prop.Value
    }

    return $current
}

if (-not (Test-Path -LiteralPath $InputDir)) {
    throw "Input directory not found: $InputDir"
}

if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$allowedLicenses = @("ORC", "ORC Notice", "OGL")
$classFiles = @(Get-ChildItem -LiteralPath $InputDir -Filter *.json -File -Recurse)

if ($classFiles.Count -eq 0) {
    throw "No class JSON files found in $InputDir"
}

$classes = @()
$licenseErrors = @()
$duplicateIdErrors = @()
$parseErrors = @()
$seenIds = New-Object System.Collections.Generic.HashSet[string]

foreach ($file in $classFiles) {
    $json = $null
    try {
        $raw = Get-Content -LiteralPath $file.FullName -Raw
        $json = $raw | ConvertFrom-Json
    }
    catch {
        $parseErrors += [PSCustomObject]@{
            file = $file.FullName
            error = $_.Exception.Message
        }
        continue
    }

    $entryType = Get-NestedValue -Object $json -Path @("type")
    if ($entryType -ne "class") {
        continue
    }

    $classId = [System.IO.Path]::GetFileNameWithoutExtension($file.Name).ToLowerInvariant()
    if (-not $seenIds.Add($classId)) {
        $duplicateIdErrors += [PSCustomObject]@{
            file = $file.FullName
            id = $classId
        }
        continue
    }

    $license = $null
    $candidateLicenses = @(
        (Get-NestedValue -Object $json -Path @("system", "publication", "license")),
        (Get-NestedValue -Object $json -Path @("system", "license")),
        (Get-NestedValue -Object $json -Path @("_stats", "license")),
        (Get-NestedValue -Object $json -Path @("system", "source", "license"))
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

    $className = Get-NestedValue -Object $json -Path @("name")
    if ([string]::IsNullOrWhiteSpace($className)) {
        continue
    }

    $keyAbilityOptions = @()
    $keyAbilityRaw = Get-NestedValue -Object $json -Path @("system", "keyAbility", "value")
    if ($null -ne $keyAbilityRaw) {
        $keyAbilityOptions = @(
            @($keyAbilityRaw) | ForEach-Object {
                "$_".Trim().ToLowerInvariant()
            } | Where-Object { -not [string]::IsNullOrWhiteSpace($_) }
        )
    }

    $spellcastingFlag = 0
    $spellcastingRaw = Get-NestedValue -Object $json -Path @("system", "spellcasting")
    if ($null -ne $spellcastingRaw -and "$spellcastingRaw" -ne "") {
        $spellcastingFlag = [int]$spellcastingRaw
    }

    $classFeatureRefs = @()
    $items = Get-NestedValue -Object $json -Path @("system", "items")
    if ($null -ne $items) {
        $itemProperties = $items.PSObject.Properties
        foreach ($itemProp in $itemProperties) {
            $uuidProp = $itemProp.Value.PSObject.Properties["uuid"]
            if ($null -ne $uuidProp -and -not [string]::IsNullOrWhiteSpace($uuidProp.Value)) {
                $classFeatureRefs += "$($uuidProp.Value)"
            }
        }
    }
    $classFeatureRefs = @($classFeatureRefs | Sort-Object -Unique)

    $publicationTitle = Get-NestedValue -Object $json -Path @("system", "publication", "title")
    $publicationPage = Get-NestedValue -Object $json -Path @("system", "publication", "page")
    $publicationRemaster = [bool](Get-NestedValue -Object $json -Path @("system", "publication", "remaster"))

    $classes += [PSCustomObject]@{
        id = $classId
        name = $className
        publication = [PSCustomObject]@{
            license = $license
            remaster = $publicationRemaster
            title = $publicationTitle
            page = $publicationPage
        }
        keyAbilityOptions = $keyAbilityOptions
        spellcastingFlag = $spellcastingFlag
        classFeatureRefs = $classFeatureRefs
    }
}

if ($parseErrors.Count -gt 0) {
    $parseErrorFile = Join-Path $OutputDir "class-parse-errors.json"
    $parseErrors | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $parseErrorFile -Encoding UTF8
    throw "JSON parse errors detected in $($parseErrors.Count) files. See $parseErrorFile"
}

if ($duplicateIdErrors.Count -gt 0) {
    $duplicateErrorFile = Join-Path $OutputDir "class-duplicate-id-errors.json"
    $duplicateIdErrors | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $duplicateErrorFile -Encoding UTF8
    throw "Duplicate class IDs detected in $($duplicateIdErrors.Count) files. See $duplicateErrorFile"
}

if ($licenseErrors.Count -gt 0) {
    $licenseErrorFile = Join-Path $OutputDir "class-license-errors.json"
    $licenseErrors | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $licenseErrorFile -Encoding UTF8
    throw "License validation failed for $($licenseErrors.Count) files. See $licenseErrorFile"
}

if ($classes.Count -eq 0) {
    throw "No valid class entries produced by importer."
}

$classes = $classes | Sort-Object -Property name
$datasetVersion = Get-Date -Format "yyyyMMdd-HHmmss"

$normalizedPath = Join-Path $OutputDir "classes.normalized.json"
$attributionPath = Join-Path $OutputDir "classes.attribution.json"
$changelogPath = Join-Path $OutputDir "classes.changelog.json"

$normalized = [PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    classCount = $classes.Count
    classes = $classes
}

$attributions = $classes |
    Group-Object -Property { $_.publication.license } |
    ForEach-Object {
        [PSCustomObject]@{
            license = $_.Name
            count = $_.Count
            sources = ($_.Group.publication.title | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)
        }
    }

$attribution = [PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    entries = $attributions
}

$changelog = [PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    added = $classes.Count
    changed = 0
    removed = 0
}

$normalized | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $normalizedPath -Encoding UTF8
$attribution | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $attributionPath -Encoding UTF8
$changelog | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $changelogPath -Encoding UTF8

Write-Host "Generated class dataset:"
Write-Host "  - $normalizedPath"
Write-Host "  - $attributionPath"
Write-Host "  - $changelogPath"
