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

function Convert-FoundryMarkupToPlainText {
    param(
        [Parameter(Mandatory = $false)]
        [string]$InputText
    )

    if ([string]::IsNullOrWhiteSpace($InputText)) {
        return ""
    }

    $text = $InputText

    # Foundry inline UUID links with explicit label: keep only human label.
    $text = [regex]::Replace($text, '@UUID\[[^\]]+\]\{([^}]+)\}', '$1')
    # UUID links without explicit label: keep trailing segment as readable fallback.
    $text = [regex]::Replace($text, '@UUID\[[^\]]*\.([^.\]]+)\]', '$1')

    # Convert common Foundry inline macros into readable text.
    $text = [regex]::Replace($text, '@Damage\[(.*?)\]', 'Damage: $1')
    $text = [regex]::Replace($text, '@Check\[(.*?)\]', 'Check: $1')
    $text = [regex]::Replace($text, '@Template\[(.*?)\]', 'Template: $1')
    $text = [regex]::Replace($text, '@Localize\[(.*?)\]', '$1')
    $text = [regex]::Replace($text, '\[\[/[^\]]+\]\]', '')

    # Normalize block-level HTML into readable plain text structure.
    $text = [regex]::Replace($text, '<\s*br\s*/?\s*>', "`n")
    $text = [regex]::Replace($text, '<\s*hr\s*/?\s*>', "`n---`n")
    $text = [regex]::Replace($text, '<\s*/p\s*>', "`n")
    $text = [regex]::Replace($text, '<\s*p[^>]*\s*>', '')
    $text = [regex]::Replace($text, '<\s*li[^>]*\s*>', '- ')
    $text = [regex]::Replace($text, '<\s*/li\s*>', "`n")
    $text = [regex]::Replace($text, '<\s*/?(ul|ol)[^>]*\s*>', "`n")

    # Strip remaining HTML tags and decode entities.
    $text = [regex]::Replace($text, '<[^>]+>', '')
    $text = [System.Net.WebUtility]::HtmlDecode($text)

    # Whitespace cleanup.
    $text = $text -replace "`r", ""
    $text = $text -replace "[`t ]+", " "
    $text = $text -replace " *`n *", "`n"
    $text = $text -replace "(`n){3,}", "`n`n"

    return $text.Trim()
}

if (-not (Test-Path -LiteralPath $InputDir)) {
    throw "Input directory not found: $InputDir"
}

if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$allowedLicenses = @("ORC", "ORC Notice", "OGL")
$spellFiles = @(Get-ChildItem -LiteralPath $InputDir -Filter *.json -File -Recurse)

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
    if ($entryType -ne "spell") {
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

    $level = 0
    $levelValue = Get-NestedValue -Object $json -Path @("system", "level", "value")
    if ($null -ne $levelValue -and "$levelValue" -ne "") {
        $level = [int]$levelValue
    }

    $traits = @()
    $traitsValue = Get-NestedValue -Object $json -Path @("system", "traits", "value")
    if ($null -ne $traitsValue) {
        $traits = @($traitsValue)
    }
    # Foundry cantrip entries can report level=1 while being true cantrips by trait/path.
    # Normalize these to rank 0 for app/runtime consistency.
    $isCantripByTrait = $traits |
        ForEach-Object { "$_".Trim().ToLowerInvariant() } |
        Where-Object { $_ -eq "cantrip" } |
        Select-Object -First 1
    $isCantripByPath = $file.FullName.ToLowerInvariant().Contains("\cantrip\")
    $isCantrip = ($null -ne $isCantripByTrait) -or $isCantripByPath
    $normalizedRank = if ($isCantrip) { 0 } else { $level }

    $traditions = @()
    $traditionsValue = Get-NestedValue -Object $json -Path @("system", "traits", "traditions")
    if ($null -ne $traditionsValue) {
        $traditions = @($traditionsValue)
    }

    $publicationTitle = Get-NestedValue -Object $json -Path @("system", "publication", "title")
    $publicationPage = Get-NestedValue -Object $json -Path @("system", "publication", "page")
    $spellName = Get-NestedValue -Object $json -Path @("name")
    if ([string]::IsNullOrWhiteSpace($spellName)) {
        continue
    }
    $rarity = Get-NestedValue -Object $json -Path @("system", "traits", "rarity")
    $castValue = Get-NestedValue -Object $json -Path @("system", "time", "value")
    $rangeValue = Get-NestedValue -Object $json -Path @("system", "range", "value")
    $targetValue = Get-NestedValue -Object $json -Path @("system", "target", "value")
    $areaValue = Get-NestedValue -Object $json -Path @("system", "area", "value")
    $areaTypeValue = Get-NestedValue -Object $json -Path @("system", "area", "type")
    $durationValue = Get-NestedValue -Object $json -Path @("system", "duration", "value")
    $saveValue = Get-NestedValue -Object $json -Path @("system", "defense", "save", "basic")
    $saveStatisticValue = Get-NestedValue -Object $json -Path @("system", "defense", "save", "statistic")
    $descriptionRaw = Get-NestedValue -Object $json -Path @("system", "description", "value")
    $descriptionValue = Convert-FoundryMarkupToPlainText -InputText $descriptionRaw

    $spells += [PSCustomObject]@{
        id = $spellId
        name = $spellName
        rank = $normalizedRank
        rarity = $rarity
        traits = $traits
        traditions = $traditions
        cast = $castValue
        range = $rangeValue
        target = $targetValue
        area = if ($null -ne $areaValue) {
            [PSCustomObject]@{ value = $areaValue; type = $areaTypeValue }
        } else { $null }
        duration = $durationValue
        save = if (-not [string]::IsNullOrWhiteSpace("$saveStatisticValue")) {
            [PSCustomObject]@{ basic = ($saveValue -eq $true); statistic = "$saveStatisticValue" }
        } else { $null }
        source = [PSCustomObject]@{
            book = $publicationTitle
            page = $publicationPage
        }
        license = $license
        descriptionRaw = $descriptionRaw
        description = $descriptionValue
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
