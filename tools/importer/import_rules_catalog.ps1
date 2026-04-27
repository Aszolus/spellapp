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
Add-Type -AssemblyName System.Web.Extensions

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
        $raw = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
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

function Normalize-RulesLookupSlug {
    param(
        [Parameter(Mandatory = $false)]
        [string]$Value
    )

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }

    $normalized = $Value.Trim().ToLowerInvariant()
    $normalized = [regex]::Replace($normalized, "[\s_]+", "-")
    $normalized = [regex]::Replace($normalized, "[^a-z0-9\-]", "")
    $normalized = [regex]::Replace($normalized, "-+", "-")
    return $normalized.Trim('-')
}

function Format-RulesLookupLabel {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Slug
    )

    $textInfo = [System.Globalization.CultureInfo]::InvariantCulture.TextInfo
    return (
        $Slug -split "-" |
            Where-Object { -not [string]::IsNullOrWhiteSpace($_) } |
            ForEach-Object {
                if ($_ -match '^\d+$') {
                    $_
                } else {
                    $textInfo.ToTitleCase($_)
                }
            }
    ) -join " "
}

function Get-LocalizationValueByKey {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Root,

        [Parameter(Mandatory = $true)]
        [string]$Key
    )

    $current = $Root
    foreach ($segment in $Key.Split('.')) {
        if ($current -isnot [System.Collections.IDictionary]) {
            return $null
        }

        $keys = @($current.Keys)
        $resolvedKey = $keys | Where-Object { $_ -ceq $segment } | Select-Object -First 1
        if ($null -eq $resolvedKey) {
            $resolvedKey = $keys | Where-Object { "$_" -ieq $segment } | Select-Object -First 1
        }
        if ($null -eq $resolvedKey) {
            return $null
        }

        $current = $current[$resolvedKey]
    }

    if ($current -is [string]) {
        return "$current"
    }

    return $null
}

function Get-TraitLabelLocalizationKey {
    param(
        [Parameter(Mandatory = $true)]
        [string]$DescriptionKey
    )

    if ($DescriptionKey -match '\.TraitDescription\.') {
        return ($DescriptionKey -replace '\.TraitDescription\.', '.Trait.')
    }

    if ($DescriptionKey -match 'TraitDescription') {
        return ($DescriptionKey -replace 'TraitDescription', 'Trait')
    }

    if ($DescriptionKey -match 'Description$') {
        return ($DescriptionKey -replace 'Description$', '')
    }

    return $null
}

function Resolve-LocalizedMarkup {
    param(
        [Parameter(Mandatory = $false)]
        [string]$InputText,

        [Parameter(Mandatory = $true)]
        [object]$LocalizationRoot
    )

    if ([string]::IsNullOrWhiteSpace($InputText)) {
        return $InputText
    }

    return [regex]::Replace(
        $InputText,
        '@Localize\[(?<key>[^\]]+)\]',
        {
            param($match)
            $key = $match.Groups['key'].Value
            $localized = Get-LocalizationValueByKey -Root $LocalizationRoot -Key $key
            if ([string]::IsNullOrWhiteSpace($localized)) {
                return $key
            }
            return $localized
        }
    )
}

function Resolve-Pf2eRepoPath {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Pf2eRepoRoot,

        [Parameter(Mandatory = $true)]
        [string]$RelativePath
    )

    $normalizedRelativePath = $RelativePath.Replace('/', '\').TrimStart('\')
    $candidates = @(
        (Join-Path $Pf2eRepoRoot $normalizedRelativePath),
        (Join-Path $Pf2eRepoRoot ($normalizedRelativePath -replace '^packs\\', 'packs\pf2e\')),
        (Join-Path $Pf2eRepoRoot (Join-Path "static" $normalizedRelativePath)),
        (Join-Path $Pf2eRepoRoot ($normalizedRelativePath -replace '^lang\\', 'static\lang\'))
    ) | Select-Object -Unique

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path $candidate).Path
        }
    }

    throw "Unable to resolve PF2e repo path '$RelativePath' under '$Pf2eRepoRoot'."
}

function Get-Pf2ePackRegistry {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Pf2eRepoRoot
    )

    $manifestPath = Join-Path $Pf2eRepoRoot "system.pf2e.json"
    if (-not (Test-Path -LiteralPath $manifestPath)) {
        throw "PF2e system manifest not found: $manifestPath"
    }

    $manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
    $packsByName = @{}
    foreach ($pack in @($manifest.packs)) {
        $packName = "$($pack.name)".Trim()
        $packPath = "$($pack.path)".Trim()
        if ([string]::IsNullOrWhiteSpace($packName) -or [string]::IsNullOrWhiteSpace($packPath)) {
            continue
        }
        $resolvedPackPath = $null
        try {
            $resolvedPackPath = Resolve-Pf2eRepoPath -Pf2eRepoRoot $Pf2eRepoRoot -RelativePath $packPath
        } catch {
            $resolvedPackPath = $null
        }
        $packsByName[$packName] = [PSCustomObject]@{
            name = $packName
            path = $resolvedPackPath
            relativePath = $packPath
            type = "$($pack.type)"
            label = "$($pack.label)"
        }
    }

    return [PSCustomObject]@{
        manifest = $manifest
        packsByName = $packsByName
    }
}

function Add-FlatLocalizationEntries {
    param(
        [Parameter(Mandatory = $false)]
        [object]$Value,

        [Parameter(Mandatory = $false)]
        [string]$Prefix,

        [Parameter(Mandatory = $true)]
        [hashtable]$Target
    )

    if ($null -eq $Value) {
        return
    }

    if ($Value -is [string]) {
        if (-not [string]::IsNullOrWhiteSpace($Prefix)) {
            $Target[$Prefix.ToLowerInvariant()] = "$Value"
        }
        return
    }

    if ($Value -is [System.Collections.IDictionary]) {
        foreach ($key in @($Value.Keys) | Sort-Object) {
            $nextPrefix = if ([string]::IsNullOrWhiteSpace($Prefix)) {
                "$key"
            } else {
                "$Prefix.$key"
            }
            Add-FlatLocalizationEntries -Value $Value[$key] -Prefix $nextPrefix -Target $Target
        }
        return
    }

    if (
        ($Value -is [System.Collections.IEnumerable]) -and
        ($Value -isnot [string])
    ) {
        foreach ($entry in @($Value)) {
            Add-FlatLocalizationEntries -Value $entry -Prefix $Prefix -Target $Target
        }
    }
}

function Get-FlatLocalizationValueByKey {
    param(
        [Parameter(Mandatory = $true)]
        [hashtable]$Entries,

        [Parameter(Mandatory = $true)]
        [string]$Key
    )

    $normalizedKey = $Key.Trim().ToLowerInvariant()
    if ($Entries.ContainsKey($normalizedKey)) {
        return "$($Entries[$normalizedKey])"
    }
    return $null
}

function Resolve-LocalizedMarkupFromEntries {
    param(
        [Parameter(Mandatory = $false)]
        [string]$InputText,

        [Parameter(Mandatory = $true)]
        [hashtable]$LocalizationEntries
    )

    if ([string]::IsNullOrWhiteSpace($InputText)) {
        return $InputText
    }

    $current = "$InputText"
    for ($iteration = 0; $iteration -lt 8; $iteration++) {
        $replaced = $false
        $next = [regex]::Replace(
            $current,
            '@Localize\[(?<key>[^\]]+)\]',
            {
                param($match)
                $key = $match.Groups['key'].Value
                $localized = Get-FlatLocalizationValueByKey -Entries $LocalizationEntries -Key $key
                if ([string]::IsNullOrWhiteSpace($localized)) {
                    return $key
                }
                $replaced = $true
                return $localized
            }
        )
        $current = $next
        if (-not $replaced) {
            break
        }
    }
    return $current
}

function Get-EnglishLocalizationDataset {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Pf2eRepoRoot,

        [Parameter(Mandatory = $true)]
        [object]$Manifest,

        [Parameter(Mandatory = $true)]
        [string]$SourceCommit,

        [Parameter(Mandatory = $true)]
        [string]$DatasetVersion
    )

    $serializer = New-Object System.Web.Script.Serialization.JavaScriptSerializer
    $serializer.MaxJsonLength = 100MB
    $entries = @{}
    foreach ($language in @($Manifest.languages) | Where-Object { "$($_.lang)".Trim().ToLowerInvariant() -eq "en" }) {
        $relativePath = "$($language.path)".Trim()
        if ([string]::IsNullOrWhiteSpace($relativePath)) {
            continue
        }
        $languagePath = Resolve-Pf2eRepoPath -Pf2eRepoRoot $Pf2eRepoRoot -RelativePath $relativePath
        $languageRoot = $serializer.DeserializeObject(
            (Get-Content -LiteralPath $languagePath -Raw -Encoding UTF8)
        )
        Add-FlatLocalizationEntries -Value $languageRoot -Prefix "" -Target $entries
    }

    $orderedEntries = [ordered]@{}
    foreach ($key in @($entries.Keys) | Sort-Object) {
        $orderedEntries[$key] = $entries[$key]
    }

    return [PSCustomObject]@{
        datasetVersion = $DatasetVersion
        sourceCommit = $SourceCommit
        generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        counts = [PSCustomObject]@{
            entries = $orderedEntries.Count
            sourceFiles = @($Manifest.languages | Where-Object { "$($_.lang)".Trim().ToLowerInvariant() -eq "en" }).Count
        }
        entries = [PSCustomObject]$orderedEntries
    }
}

function Get-FoundryReferenceIndexDataset {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Pf2eRepoRoot,

        [Parameter(Mandatory = $true)]
        [hashtable]$PackRegistry,

        [Parameter(Mandatory = $true)]
        [hashtable]$LocalizationEntries,

        [Parameter(Mandatory = $true)]
        [string]$SourceCommit,

        [Parameter(Mandatory = $true)]
        [string]$DatasetVersion
    )

    $traitsConfigPath = Join-Path $Pf2eRepoRoot "src/scripts/config/traits.ts"
    if (-not (Test-Path -LiteralPath $traitsConfigPath)) {
        throw "Trait config not found: $traitsConfigPath"
    }

    $targetPacks = @(
        [PSCustomObject]@{ packName = "actionspf2e"; category = "ACTION" },
        [PSCustomObject]@{ packName = "conditionitems"; category = "CONDITION" },
        [PSCustomObject]@{ packName = "spell-effects"; category = "SPELL_EFFECT" },
        [PSCustomObject]@{ packName = "feats-srd"; category = "FEAT" },
        [PSCustomObject]@{ packName = "spells-srd"; category = "SPELL" },
        [PSCustomObject]@{ packName = "equipment-srd"; category = "ITEM" }
    )

    $traitDescriptionKeyBySlug = @{}
    $inTraitDescriptions = $false
    foreach ($line in Get-Content -LiteralPath $traitsConfigPath -Encoding UTF8) {
        if (-not $inTraitDescriptions) {
            if ($line -match '^\s*const traitDescriptions = \{') {
                $inTraitDescriptions = $true
            }
            continue
        }

        if ($line -match '^\s*\};\s*$') {
            break
        }

        if ($line -match '^\s*"(?<slug>[^"]+)"\s*:\s*"(?<key>[^"]+)"\s*,?\s*$') {
            $traitDescriptionKeyBySlug[(Normalize-RulesLookupSlug -Value $matches.slug)] = $matches.key
            continue
        }

        if ($line -match '^\s*(?<slug>[a-z0-9_]+)\s*:\s*"(?<key>[^"]+)"\s*,?\s*$') {
            $traitDescriptionKeyBySlug[(Normalize-RulesLookupSlug -Value $matches.slug)] = $matches.key
        }
    }

    $traitsSection = [ordered]@{}
    foreach ($traitSlug in @($traitDescriptionKeyBySlug.Keys) | Sort-Object) {
        $descriptionKey = $traitDescriptionKeyBySlug[$traitSlug]
        $descriptionRaw = Get-FlatLocalizationValueByKey -Entries $LocalizationEntries -Key $descriptionKey
        $descriptionRaw = Resolve-LocalizedMarkupFromEntries -InputText $descriptionRaw -LocalizationEntries $LocalizationEntries
        if ([string]::IsNullOrWhiteSpace($descriptionRaw)) {
            continue
        }

        $label = $null
        $labelKey = Get-TraitLabelLocalizationKey -DescriptionKey $descriptionKey
        if (-not [string]::IsNullOrWhiteSpace($labelKey)) {
            $label = Get-FlatLocalizationValueByKey -Entries $LocalizationEntries -Key $labelKey
        }
        if ([string]::IsNullOrWhiteSpace($label)) {
            $label = Format-RulesLookupLabel -Slug $traitSlug
        }

        $traitsSection[$traitSlug] = [ordered]@{
            slug = $traitSlug
            label = "$label"
            descriptionRaw = "$descriptionRaw"
        }
    }

    $referenceEntries = @()
    foreach ($spec in $targetPacks) {
        $pack = $PackRegistry[$spec.packName]
        if ($null -eq $pack) {
            throw "Required PF2e pack '$($spec.packName)' was not found in system.pf2e.json."
        }
        if ([string]::IsNullOrWhiteSpace($pack.path) -or -not (Test-Path -LiteralPath $pack.path)) {
            throw "Required PF2e pack '$($spec.packName)' could not be resolved from system.pf2e.json path '$($pack.relativePath)'."
        }

        $files = @(
            Get-ChildItem -LiteralPath $pack.path -Filter *.json -File -Recurse |
                Where-Object { $_.Name -ne "_folders.json" } |
                Sort-Object -Property FullName
        )
        foreach ($file in $files) {
            $json = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
            $name = "$((Get-NestedValue -Object $json -Path @("name")))"
            $type = "$((Get-NestedValue -Object $json -Path @("type")))"
            $id = "$((Get-NestedValue -Object $json -Path @("_id")))"
            if ([string]::IsNullOrWhiteSpace($name)) {
                continue
            }

            $descriptionRaw = Get-NestedValue -Object $json -Path @("system", "description", "value")
            $actionType = Get-NestedValue -Object $json -Path @("system", "actionType", "value")
            $actions = Get-NestedValue -Object $json -Path @("system", "actions", "value")
            $traits = Normalize-StringList -RawValue (Get-NestedValue -Object $json -Path @("system", "traits", "value"))
            $aliases = @()
            if (-not [string]::IsNullOrWhiteSpace($id)) {
                $aliases += "Compendium.pf2e.$($spec.packName).Item.$id"
            }
            $aliases += "Compendium.pf2e.$($spec.packName).Item.$name"
            $aliases = @($aliases | Where-Object { -not [string]::IsNullOrWhiteSpace($_) } | Select-Object -Unique)

            $canonicalUuid = $aliases | Select-Object -First 1
            $referenceEntries += [PSCustomObject]@{
                uuid = "$canonicalUuid"
                packName = "$($spec.packName)"
                category = "$($spec.category)"
                name = "$name"
                type = if ([string]::IsNullOrWhiteSpace($type)) { $null } else { "$type" }
                descriptionRaw = if ($null -eq $descriptionRaw) { "" } else { "$descriptionRaw" }
                traits = $traits
                actionType = if ([string]::IsNullOrWhiteSpace($actionType)) { $null } else { "$actionType" }
                actions = $actions
                aliases = $aliases
            }
        }
    }

    $referenceEntries = @(
        $referenceEntries |
            Sort-Object -Property category, name, uuid
    )
    $aliasCount = @($referenceEntries | ForEach-Object { @($_.aliases).Count } | Measure-Object -Sum).Sum

    return [PSCustomObject]@{
        datasetVersion = $DatasetVersion
        sourceCommit = $SourceCommit
        generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
        counts = [PSCustomObject]@{
            traitDefinitions = $traitsSection.Count
            referenceEntries = $referenceEntries.Count
            aliases = $aliasCount
        }
        traits = [PSCustomObject]$traitsSection
        references = $referenceEntries
    }
}

function Get-RulesReferenceShardKeyForCategory {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Category
    )

    switch ($Category.Trim().ToUpperInvariant()) {
        "CONDITION" { return "conditions" }
        "ACTION" { return "actions" }
        "SPELL_EFFECT" { return "spell-effects" }
        "FEAT" { return "feats" }
        "SPELL" { return "spells" }
        "ITEM" { return "items" }
        default { return $null }
    }
}

function Get-RulesReferenceShardFileName {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ShardKey
    )

    return "rules.reference.$($ShardKey).json.gz"
}

function Write-GzipJsonFile {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Data,

        [Parameter(Mandatory = $true)]
        [string]$Path,

        [int]$Depth = 30
    )

    $json = $Data | ConvertTo-Json -Depth $Depth -Compress
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
    $fileStream = [System.IO.File]::Open($Path, [System.IO.FileMode]::Create, [System.IO.FileAccess]::Write, [System.IO.FileShare]::None)
    try {
        $gzipStream = New-Object System.IO.Compression.GZipStream($fileStream, [System.IO.Compression.CompressionLevel]::Optimal)
        try {
            $gzipStream.Write($bytes, 0, $bytes.Length)
        }
        finally {
            $gzipStream.Dispose()
        }
    }
    finally {
        $fileStream.Dispose()
    }
}

function Write-FoundryReferenceIndexAssets {
    param(
        [Parameter(Mandatory = $true)]
        [object]$Dataset,

        [Parameter(Mandatory = $true)]
        [string]$OutputDir,

        [Parameter(Mandatory = $true)]
        [string]$ManifestPath
    )

    $traitShardKey = "traits"
    $traitShardFileName = Get-RulesReferenceShardFileName -ShardKey $traitShardKey
    $traitRecords = [ordered]@{}
    foreach ($property in $Dataset.traits.PSObject.Properties) {
        $value = $property.Value
        $traitRecords[$property.Name] = [ordered]@{
            label = "$($value.label)"
            descriptionRaw = "$($value.descriptionRaw)"
        }
    }

    $referenceEntriesByShard = @{}
    foreach ($entry in @($Dataset.references)) {
        $shardKey = Get-RulesReferenceShardKeyForCategory -Category "$($entry.category)"
        if ([string]::IsNullOrWhiteSpace($shardKey)) {
            continue
        }
        if (-not $referenceEntriesByShard.ContainsKey($shardKey)) {
            $referenceEntriesByShard[$shardKey] = New-Object System.Collections.ArrayList
        }
        $aliases = @(
            @($entry.aliases) |
                Where-Object { -not [string]::IsNullOrWhiteSpace("$_") } |
                ForEach-Object { "$_" }
        )
        $null = $referenceEntriesByShard[$shardKey].Add(
            [ordered]@{
                uuid = "$($entry.uuid)"
                name = "$($entry.name)"
                type = if ([string]::IsNullOrWhiteSpace("$($entry.type)")) { $null } else { "$($entry.type)" }
                descriptionRaw = if ($null -eq $entry.descriptionRaw) { "" } else { "$($entry.descriptionRaw)" }
                aliases = $aliases
            }
        )
    }

    $shards = [ordered]@{}
    $shards[$traitShardKey] = [ordered]@{
        file = $traitShardFileName
        count = $traitRecords.Count
    }

    Write-GzipJsonFile -Data ([PSCustomObject]$traitRecords) -Path (Join-Path $OutputDir $traitShardFileName) -Depth 20

    foreach ($shardKey in @($referenceEntriesByShard.Keys) | Sort-Object) {
        $entries = @($referenceEntriesByShard[$shardKey])
        $shardFileName = Get-RulesReferenceShardFileName -ShardKey $shardKey
        Write-GzipJsonFile -Data $entries -Path (Join-Path $OutputDir $shardFileName) -Depth 20
        $aliasCount = @($entries | ForEach-Object { @($_.aliases).Count } | Measure-Object -Sum).Sum
        if ($null -eq $aliasCount) {
            $aliasCount = 0
        }
        $shards[$shardKey] = [ordered]@{
            file = $shardFileName
            count = $entries.Count
            aliases = [int]$aliasCount
        }
    }

    $manifest = [ordered]@{
        datasetVersion = $Dataset.datasetVersion
        sourceCommit = $Dataset.sourceCommit
        generatedAtUtc = $Dataset.generatedAtUtc
        counts = $Dataset.counts
        shards = [PSCustomObject]$shards
    }
    $manifest | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $ManifestPath -Encoding UTF8

    return [PSCustomObject]@{
        manifestPath = $ManifestPath
        shardPaths = @($shards.Values | ForEach-Object { Join-Path $OutputDir $_.file })
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
            $raw = Get-Content -LiteralPath $file.FullName -Raw -Encoding UTF8
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
$referenceIndexPath = Join-Path $OutputDir "rules.reference.index.json"
$localizationPath = Join-Path $OutputDir "foundry.localization.en.flat.json"

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

$pf2eRepoRoot = (Resolve-Path (Join-Path ((Resolve-Path $PacksDir).Path) "..\..")).Path
$packRegistry = Get-Pf2ePackRegistry -Pf2eRepoRoot $pf2eRepoRoot
$localizationDataset = Get-EnglishLocalizationDataset `
    -Pf2eRepoRoot $pf2eRepoRoot `
    -Manifest $packRegistry.manifest `
    -SourceCommit $SourceCommit `
    -DatasetVersion $datasetVersion
$localizationEntries = @{}
foreach ($property in $localizationDataset.entries.PSObject.Properties) {
    $localizationEntries[$property.Name] = $property.Value
}
$referenceIndexDataset = Get-FoundryReferenceIndexDataset `
    -Pf2eRepoRoot $pf2eRepoRoot `
    -PackRegistry $packRegistry.packsByName `
    -LocalizationEntries $localizationEntries `
    -SourceCommit $SourceCommit `
    -DatasetVersion $datasetVersion

$normalized | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $normalizedPath -Encoding UTF8
$attribution | ConvertTo-Json -Depth 30 | Set-Content -LiteralPath $attributionPath -Encoding UTF8
$changelog | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $changelogPath -Encoding UTF8
$referenceAssets = Write-FoundryReferenceIndexAssets `
    -Dataset $referenceIndexDataset `
    -OutputDir $OutputDir `
    -ManifestPath $referenceIndexPath
$localizationDataset | ConvertTo-Json -Depth 50 | Set-Content -LiteralPath $localizationPath -Encoding UTF8

Write-Host "Generated rules catalog dataset:"
Write-Host "  - $normalizedPath"
Write-Host "  - $attributionPath"
Write-Host "  - $changelogPath"
Write-Host "  - $($referenceAssets.manifestPath)"
foreach ($shardPath in $referenceAssets.shardPaths) {
    Write-Host "  - $shardPath"
}
Write-Host "  - $localizationPath"
