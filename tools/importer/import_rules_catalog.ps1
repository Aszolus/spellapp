param(
    [Parameter(Mandatory = $true)]
    [string]$PacksDir,

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
    $text = [regex]::Replace($text, '@UUID\[[^\]]+\]\{([^}]+)\}', '$1')
    $text = [regex]::Replace($text, '@UUID\[[^\]]*\.([^.\]]+)\]', '$1')
    $text = [regex]::Replace($text, '@(Damage|Check|Template|Localize)\[(.*?)\]', '$2')
    $text = [regex]::Replace($text, '<\s*br\s*/?\s*>', "`n")
    $text = [regex]::Replace($text, '<\s*hr\s*/?\s*>', "`n---`n")
    $text = [regex]::Replace($text, '<\s*/p\s*>', "`n")
    $text = [regex]::Replace($text, '<\s*p[^>]*\s*>', '')
    $text = [regex]::Replace($text, '<\s*li[^>]*\s*>', '- ')
    $text = [regex]::Replace($text, '<\s*/li\s*>', "`n")
    $text = [regex]::Replace($text, '<\s*/?(ul|ol|h1|h2|h3|h4|h5|h6)[^>]*\s*>', "`n")
    $text = [regex]::Replace($text, '<[^>]+>', '')
    $text = [System.Net.WebUtility]::HtmlDecode($text)
    $text = $text -replace "`r", ""
    $text = $text -replace "[`t ]+", " "
    $text = $text -replace " *`n *", "`n"
    $text = $text -replace "(`n){3,}", "`n`n"
    return $text.Trim()
}

function Get-License {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Json
    )

    $candidates = @(
        (Get-NestedValue -Object $Json -Path @("system", "publication", "license")),
        (Get-NestedValue -Object $Json -Path @("system", "license")),
        (Get-NestedValue -Object $Json -Path @("_stats", "license")),
        (Get-NestedValue -Object $Json -Path @("system", "source", "license"))
    )

    foreach ($candidate in $candidates) {
        if (-not [string]::IsNullOrWhiteSpace($candidate)) {
            return "$candidate"
        }
    }
    return $null
}

function Normalize-StringList {
    param(
        [Parameter(Mandatory = $false)]
        [object]$RawValue
    )

    if ($null -eq $RawValue) {
        return ,@()
    }

    $values = @(
        @($RawValue) | ForEach-Object {
            "$_".Trim()
        } | Where-Object {
            -not [string]::IsNullOrWhiteSpace($_)
        } | Select-Object -Unique
    )
    return ,$values
}

function Extract-Prerequisites {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Json
    )

    $raw = Get-NestedValue -Object $Json -Path @("system", "prerequisites", "value")
    if ($null -eq $raw) {
        return ,@()
    }

    $values = @()
    foreach ($entry in @($raw)) {
        if ($entry -is [string]) {
            $values += $entry
            continue
        }
        $valueProp = $entry.PSObject.Properties["value"]
        if ($null -ne $valueProp -and -not [string]::IsNullOrWhiteSpace($valueProp.Value)) {
            $values += "$($valueProp.Value)"
        }
    }
    return ,@($values | Select-Object -Unique)
}

function Extract-RuleKeys {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Json
    )

    $rules = Get-NestedValue -Object $Json -Path @("system", "rules")
    if ($null -eq $rules) {
        return ,@()
    }

    $keys = @()
    foreach ($rule in @($rules)) {
        $key = $rule.PSObject.Properties["key"]
        if ($null -ne $key -and -not [string]::IsNullOrWhiteSpace($key.Value)) {
            $keys += "$($key.Value)"
        }
    }
    return ,@($keys | Select-Object -Unique)
}

function Extract-LinkedItemUuids {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Json
    )

    $items = Get-NestedValue -Object $Json -Path @("system", "items")
    if ($null -eq $items) {
        return ,@()
    }

    $uuids = @()
    foreach ($prop in $items.PSObject.Properties) {
        $uuidProp = $prop.Value.PSObject.Properties["uuid"]
        if ($null -ne $uuidProp -and -not [string]::IsNullOrWhiteSpace($uuidProp.Value)) {
            $uuids += "$($uuidProp.Value)"
        }
    }
    return ,@($uuids | Select-Object -Unique)
}

function Get-SignalFlags {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Name,

        [Parameter(Mandatory = $true)]
        [string]$DescriptionPlain,

        [Parameter(Mandatory = $false)]
        [string[]]$Prerequisites = @(),

        [Parameter(Mandatory = $false)]
        [string[]]$OtherTags = @(),

        [Parameter(Mandatory = $false)]
        [string[]]$RuleKeys = @(),

        [Parameter(Mandatory = $false)]
        [string[]]$LinkedItemUuids = @()
    )

    $Prerequisites = @($Prerequisites | Where-Object { $_ -ne $null })
    $OtherTags = @($OtherTags | Where-Object { $_ -ne $null })
    $RuleKeys = @($RuleKeys | Where-Object { $_ -ne $null })
    $LinkedItemUuids = @($LinkedItemUuids | Where-Object { $_ -ne $null })

    $haystack = ("$Name`n$DescriptionPlain`n" + ($Prerequisites -join " ")).ToLowerInvariant()
    $grantsCastASpellAction = (
        $haystack -match 'cast a spell activity' -or
        @($LinkedItemUuids | Where-Object { $_ -match 'actionspf2e\.Item\.Cast a Spell' }).Count -gt 0
    )

    return [PSCustomObject]@{
        hasSpellcastingText = [bool]($haystack -match '\bspellcasting\b')
        hasSpellSlotText = [bool]($haystack -match '\bspell slots?\b')
        hasCantripText = [bool]($haystack -match '\bcantrips?\b')
        hasPreparedText = [bool]($haystack -match '\bprepared\b')
        hasBasicSpellcastingBenefitsText = [bool]($haystack -match '\bbasic spellcasting (feat|benefits)\b')
        hasExpertSpellcastingBenefitsText = [bool]($haystack -match '\bexpert spellcasting (feat|benefits)\b')
        hasMasterSpellcastingBenefitsText = [bool]($haystack -match '\bmaster spellcasting (feat|benefits)\b')
        hasPermanentPreparedSignal = [bool]($haystack -match 'automatically prepared|always prepared|permanently prepared')
        hasSpellcastingMulticlassTag = ($OtherTags -contains "spellcasting-multiclass-archetype")
        grantsCastASpellAction = $grantsCastASpellAction
        hasRuleKeySignal = ($RuleKeys -contains "GrantItem" -or $RuleKeys -contains "ChoiceSet")
    }
}

function Is-SpellcastingRelevant {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Signals
    )

    return (
        $Signals.hasSpellcastingText -or
        $Signals.hasSpellSlotText -or
        $Signals.hasCantripText -or
        $Signals.hasPreparedText -or
        $Signals.hasBasicSpellcastingBenefitsText -or
        $Signals.hasExpertSpellcastingBenefitsText -or
        $Signals.hasMasterSpellcastingBenefitsText -or
        $Signals.hasPermanentPreparedSignal -or
        $Signals.hasSpellcastingMulticlassTag -or
        $Signals.grantsCastASpellAction
    )
}

function Get-JournalSpellcastingReferences {
    param(
        [Parameter(Mandatory = $true)]
        [string]$JournalsDir
    )

    $results = @()
    if (-not (Test-Path -LiteralPath $JournalsDir)) {
        return ,$results
    }

    $journalFiles = @(Get-ChildItem -LiteralPath $JournalsDir -Filter *.json -File -Recurse)
    foreach ($file in $journalFiles) {
        $raw = Get-Content -LiteralPath $file.FullName -Raw
        $json = $raw | ConvertFrom-Json
        $pages = Get-NestedValue -Object $json -Path @("pages")
        if ($null -eq $pages) {
            continue
        }

        foreach ($page in @($pages)) {
            $contentRaw = Get-NestedValue -Object $page -Path @("text", "content")
            $content = Convert-FoundryMarkupToPlainText -InputText $contentRaw
            if ([string]::IsNullOrWhiteSpace($content)) {
                continue
            }
            $contentLower = $content.ToLowerInvariant()
            $hasBasic = $contentLower -match '\bbasic spellcasting feat\b'
            $hasExpert = $contentLower -match '\bexpert spellcasting feat\b'
            $hasMaster = $contentLower -match '\bmaster spellcasting feat\b'
            if (-not ($hasBasic -or $hasExpert -or $hasMaster)) {
                continue
            }

            $excerpt = $content
            if ($excerpt.Length -gt 1600) {
                $excerpt = $excerpt.Substring(0, 1600)
            }

            $results += [PSCustomObject]@{
                journalId = "$($json._id)"
                journalName = "$($json.name)"
                pageId = "$($page._id)"
                pageName = "$($page.name)"
                hasBasicSpellcastingFeat = $hasBasic
                hasExpertSpellcastingFeat = $hasExpert
                hasMasterSpellcastingFeat = $hasMaster
                excerpt = $excerpt
                sourceFile = $file.Name
            }
        }
    }

    return ,@($results | Sort-Object -Property journalName, pageName)
}

function Get-RelativePathSafe {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BaseDir,
        [Parameter(Mandatory = $true)]
        [string]$FullPath
    )

    $baseFull = [System.IO.Path]::GetFullPath($BaseDir)
    $targetFull = [System.IO.Path]::GetFullPath($FullPath)
    if ($targetFull.StartsWith($baseFull, [System.StringComparison]::OrdinalIgnoreCase)) {
        $trimmed = $targetFull.Substring($baseFull.Length).TrimStart('\', '/')
        if (-not [string]::IsNullOrWhiteSpace($trimmed)) {
            return $trimmed
        }
    }
    return [System.IO.Path]::GetFileName($targetFull)
}

function Get-ScannedCountKey {
    param(
        [Parameter(Mandatory = $true)]
        [string]$SourceType
    )

    switch ($SourceType) {
        "class" { return "classes" }
        "classFeature" { return "classFeatures" }
        "feat" { return "feats" }
        "ancestry" { return "ancestries" }
        "background" { return "backgrounds" }
        default { return "${SourceType}s" }
    }
}

if (-not (Test-Path -LiteralPath $PacksDir)) {
    throw "PF2e packs directory not found: $PacksDir"
}

if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$paths = @{
    classes = Join-Path $PacksDir "classes"
    classFeatures = Join-Path $PacksDir "class-features"
    feats = Join-Path $PacksDir "feats"
    ancestries = Join-Path $PacksDir "ancestries"
    backgrounds = Join-Path $PacksDir "backgrounds"
    journals = Join-Path $PacksDir "journals"
}

$allowedLicenses = @("ORC", "ORC Notice", "OGL")
$parseErrors = @()
$licenseErrors = @()
$duplicateErrors = @()
$records = @()
$scannedCounts = @{
    classes = 0
    classFeatures = 0
    feats = 0
    ancestries = 0
    backgrounds = 0
    journals = 0
}
$seenKeys = New-Object System.Collections.Generic.HashSet[string]

$entrySpecs = @(
    @{ sourceType = "class"; dir = $paths.classes; expectedType = "class"; includeAll = $false },
    @{ sourceType = "classFeature"; dir = $paths.classFeatures; expectedType = "feat"; includeAll = $false },
    @{ sourceType = "feat"; dir = $paths.feats; expectedType = "feat"; includeAll = $false },
    @{ sourceType = "ancestry"; dir = $paths.ancestries; expectedType = "ancestry"; includeAll = $false },
    @{ sourceType = "background"; dir = $paths.backgrounds; expectedType = "background"; includeAll = $false }
)

foreach ($spec in $entrySpecs) {
    if (-not (Test-Path -LiteralPath $spec.dir)) {
        continue
    }

    $files = @(Get-ChildItem -LiteralPath $spec.dir -Filter *.json -File -Recurse)
    foreach ($file in $files) {
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
        if ($entryType -ne $spec.expectedType) {
            continue
        }

        $scannedKey = Get-ScannedCountKey -SourceType $spec.sourceType
        $scannedCounts[$scannedKey] += 1

        $relativePath = Get-RelativePathSafe -BaseDir $spec.dir -FullPath $file.FullName
        $relativePathNormalized = $relativePath.Replace('\', '/')
        $optionId = ([regex]::Replace($relativePathNormalized, '\.[^./\\]+$', '')).ToLowerInvariant()
        $uniqueKey = "$($spec.sourceType):$optionId"
        if (-not $seenKeys.Add($uniqueKey)) {
            $duplicateErrors += [PSCustomObject]@{
                key = $uniqueKey
                file = $file.FullName
            }
            continue
        }

        $license = Get-License -Json $json
        if ([string]::IsNullOrWhiteSpace($license) -or ($allowedLicenses -notcontains $license)) {
            $licenseErrors += [PSCustomObject]@{
                file = $file.FullName
                license = $license
            }
            continue
        }

        $name = Get-NestedValue -Object $json -Path @("name")
        if ([string]::IsNullOrWhiteSpace($name)) {
            continue
        }

        $descriptionRaw = Get-NestedValue -Object $json -Path @("system", "description", "value")
        $description = Convert-FoundryMarkupToPlainText -InputText $descriptionRaw
        $levelRaw = Get-NestedValue -Object $json -Path @("system", "level", "value")
        $level = $null
        if ($null -ne $levelRaw -and "$levelRaw" -ne "") {
            $level = [int]$levelRaw
        }

        $traits = Normalize-StringList -RawValue (Get-NestedValue -Object $json -Path @("system", "traits", "value"))
        $otherTags = Normalize-StringList -RawValue (Get-NestedValue -Object $json -Path @("system", "traits", "otherTags"))
        $prerequisites = Extract-Prerequisites -Json $json
        $ruleKeys = Extract-RuleKeys -Json $json
        $linkedItemUuids = Extract-LinkedItemUuids -Json $json
        $signals = Get-SignalFlags `
            -Name "$name" `
            -DescriptionPlain "$description" `
            -Prerequisites $prerequisites `
            -OtherTags $otherTags `
            -RuleKeys $ruleKeys `
            -LinkedItemUuids $linkedItemUuids

        if ($spec.sourceType -eq "class") {
            $spellcastingFlagRaw = Get-NestedValue -Object $json -Path @("system", "spellcasting")
            $spellcastingFlag = 0
            if ($null -ne $spellcastingFlagRaw -and "$spellcastingFlagRaw" -ne "") {
                $spellcastingFlag = [int]$spellcastingFlagRaw
            }
            if ($spellcastingFlag -le 0) {
                continue
            }
        } elseif (-not (Is-SpellcastingRelevant -Signals $signals)) {
            continue
        }

        $records += [PSCustomObject]@{
            sourceType = $spec.sourceType
            optionId = $optionId
            name = "$name"
            level = $level
            category = (Get-NestedValue -Object $json -Path @("system", "category"))
            traits = $traits
            otherTags = $otherTags
            prerequisites = $prerequisites
            ruleKeys = $ruleKeys
            linkedItemUuids = $linkedItemUuids
            publication = [PSCustomObject]@{
                license = $license
                remaster = [bool](Get-NestedValue -Object $json -Path @("system", "publication", "remaster"))
                title = (Get-NestedValue -Object $json -Path @("system", "publication", "title"))
                page = (Get-NestedValue -Object $json -Path @("system", "publication", "page"))
            }
            signalFlags = $signals
            description = $description
        }
    }
}

$journalReferences = @()
try {
    $journalReferences = Get-JournalSpellcastingReferences -JournalsDir $paths.journals
    $scannedCounts.journals = @(
        Get-ChildItem -LiteralPath $paths.journals -Filter *.json -File -Recurse -ErrorAction SilentlyContinue
    ).Count
}
catch {
    $parseErrors += [PSCustomObject]@{
        file = $paths.journals
        error = "Journal parse failure: $($_.Exception.Message)"
    }
}

if ($parseErrors.Count -gt 0) {
    $parseErrorFile = Join-Path $OutputDir "rules-catalog-parse-errors.json"
    $parseErrors | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $parseErrorFile -Encoding UTF8
    throw "Parse errors detected while building rules catalog. See $parseErrorFile"
}

if ($duplicateErrors.Count -gt 0) {
    $duplicateErrorFile = Join-Path $OutputDir "rules-catalog-duplicate-errors.json"
    $duplicateErrors | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $duplicateErrorFile -Encoding UTF8
    throw "Duplicate option IDs detected in rules catalog inputs. See $duplicateErrorFile"
}

if ($licenseErrors.Count -gt 0) {
    $licenseErrorFile = Join-Path $OutputDir "rules-catalog-license-errors.json"
    $licenseErrors | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $licenseErrorFile -Encoding UTF8
    throw "License validation failed for rules catalog inputs. See $licenseErrorFile"
}

if ($records.Count -eq 0) {
    throw "No rules catalog option records produced."
}

$records = $records | Sort-Object -Property sourceType, level, name
$datasetVersion = Get-Date -Format "yyyyMMdd-HHmmss"

$normalizedPath = Join-Path $OutputDir "rules.catalog.normalized.json"
$attributionPath = Join-Path $OutputDir "rules.catalog.attribution.json"
$changelogPath = Join-Path $OutputDir "rules.catalog.changelog.json"

$normalized = [PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    scannedCounts = $scannedCounts
    catalogCounts = [PSCustomObject]@{
        classes = @($records | Where-Object { $_.sourceType -eq "class" }).Count
        classFeatures = @($records | Where-Object { $_.sourceType -eq "classFeature" }).Count
        feats = @($records | Where-Object { $_.sourceType -eq "feat" }).Count
        ancestries = @($records | Where-Object { $_.sourceType -eq "ancestry" }).Count
        backgrounds = @($records | Where-Object { $_.sourceType -eq "background" }).Count
        totalOptions = $records.Count
        journalSpellcastingReferences = $journalReferences.Count
    }
    options = $records
    journalSpellcastingReferences = $journalReferences
}

$licenseGroups = $records |
    Group-Object -Property { $_.publication.license } |
    ForEach-Object {
        [PSCustomObject]@{
            license = $_.Name
            count = $_.Count
            sourceTypes = ($_.Group.sourceType | Sort-Object -Unique)
            sources = ($_.Group.publication.title | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Sort-Object -Unique)
        }
    }

$attribution = [PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    entries = $licenseGroups
}

$changelog = [PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    added = $records.Count
    changed = 0
    removed = 0
}

$normalized | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $normalizedPath -Encoding UTF8
$attribution | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $attributionPath -Encoding UTF8
$changelog | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $changelogPath -Encoding UTF8

Write-Host "Generated rules catalog dataset:"
Write-Host "  - $normalizedPath"
Write-Host "  - $attributionPath"
Write-Host "  - $changelogPath"
