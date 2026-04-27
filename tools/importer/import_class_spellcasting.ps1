param(
    [Parameter(Mandatory = $true)]
    [string]$FoundryPf2ePacksDir,

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
        $property = $current.PSObject.Properties[$segment]
        if ($null -eq $property) {
            return $null
        }
        $current = $property.Value
    }
    return $current
}

function Read-JsonFile {
    param([Parameter(Mandatory = $true)][string]$Path)

    try {
        return Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json
    }
    catch {
        throw "Could not parse JSON file $Path`: $($_.Exception.Message)"
    }
}

function ConvertTo-Slug {
    param([Parameter(Mandatory = $true)][string]$Value)

    return ($Value.ToLowerInvariant() -replace "[^a-z0-9]+", "-").Trim("-")
}

function ConvertTo-Ability {
    param([Parameter(Mandatory = $false)][string]$Value)

    switch ($Value.ToLowerInvariant()) {
        "str" { return "STRENGTH" }
        "dex" { return "DEXTERITY" }
        "con" { return "CONSTITUTION" }
        "int" { return "INTELLIGENCE" }
        "wis" { return "WISDOM" }
        "cha" { return "CHARISMA" }
        default { return $null }
    }
}

function ConvertTo-Tradition {
    param([Parameter(Mandatory = $false)][string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return $null
    }
    switch ($Value.Trim().ToLowerInvariant()) {
        "arcane" { return "ARCANE" }
        "divine" { return "DIVINE" }
        "occult" { return "OCCULT" }
        "primal" { return "PRIMAL" }
        "variable" { return $null }
        default { return $null }
    }
}

function Remove-Html {
    param([Parameter(Mandatory = $false)][string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return ""
    }
    $withoutTags = [regex]::Replace($Value, "<[^>]+>", " ")
    $decoded = [System.Net.WebUtility]::HtmlDecode($withoutTags)
    return [regex]::Replace($decoded, "\s+", " ").Trim()
}

function Get-FeatureDescription {
    param([Parameter(Mandatory = $false)][object]$Feature)

    $description = Get-NestedValue -Object $Feature -Path @("system", "description", "value")
    if ($null -eq $description) {
        return ""
    }
    return "$description"
}

function Get-TextTradition {
    param([Parameter(Mandatory = $false)][string]$Html)

    $text = Remove-Html $Html
    $patterns = @(
        "Tradition\s+([A-Za-z]+)",
        "Spell List\s+([A-Za-z]+)",
        "cast spells of the ([A-Za-z]+) tradition",
        "cast ([A-Za-z]+) spells",
        "use the ([A-Za-z]+) spell list"
    )
    foreach ($pattern in $patterns) {
        $match = [regex]::Match($text, $pattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
        if ($match.Success) {
            $tradition = ConvertTo-Tradition $match.Groups[1].Value
            if ($null -ne $tradition) {
                return $tradition
            }
        }
    }
    return $null
}

function Get-RuleTradition {
    param([Parameter(Mandatory = $false)][object]$Feature)

    foreach ($rule in @(Get-NestedValue -Object $Feature -Path @("system", "rules"))) {
        $option = Get-NestedValue -Object $rule -Path @("option")
        if ($null -ne $option -and "$option" -match "tradition:([a-z]+)") {
            $tradition = ConvertTo-Tradition $Matches[1]
            if ($null -ne $tradition) {
                return $tradition
            }
        }
        $path = Get-NestedValue -Object $rule -Path @("path")
        $value = Get-NestedValue -Object $rule -Path @("value")
        if ($null -ne $path -and "$path" -match "spellcasting|proficiencies\.aliases" -and $null -ne $value) {
            $tradition = ConvertTo-Tradition "$value"
            if ($null -ne $tradition) {
                return $tradition
            }
        }
    }
    return $null
}

function Get-FeatureTradition {
    param([Parameter(Mandatory = $false)][object]$Feature)

    $ruleTradition = Get-RuleTradition $Feature
    if ($null -ne $ruleTradition) {
        return $ruleTradition
    }
    return Get-TextTradition (Get-FeatureDescription $Feature)
}

function Get-SpellNamesFromUuidText {
    param([Parameter(Mandatory = $false)][string]$Html)

    $names = New-Object System.Collections.Generic.List[string]
    foreach ($match in [regex]::Matches($Html, "@UUID\[Compendium\.pf2e\.spells-srd\.Item\.([^\]\}]+)(?:\]\{([^\}]+)\})?\]")) {
        $name = $match.Groups[2].Value
        if ([string]::IsNullOrWhiteSpace($name)) {
            $name = $match.Groups[1].Value
        }
        $cleaned = [System.Net.WebUtility]::HtmlDecode($name).Trim()
        if (-not [string]::IsNullOrWhiteSpace($cleaned) -and -not $names.Contains($cleaned)) {
            $names.Add($cleaned)
        }
    }
    return @($names)
}

function Get-GrantedSpellNames {
    param([Parameter(Mandatory = $false)][string]$Html)

    $names = New-Object System.Collections.Generic.List[string]
    foreach ($blockMatch in [regex]::Matches($Html, "<(?:p|li)\b[^>]*>.*?</(?:p|li)>", [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        $block = $blockMatch.Value
        $text = Remove-Html $block
        $include = $text -match "Sorcerous Gifts|Granted Spells|familiar learns"
        if (-not $include) {
            continue
        }
        foreach ($spellName in Get-SpellNamesFromUuidText $block) {
            if (-not $names.Contains($spellName)) {
                $names.Add($spellName)
            }
        }
    }
    return @($names)
}

function Get-FocusSpellNames {
    param([Parameter(Mandatory = $false)][string]$Html)

    $names = New-Object System.Collections.Generic.List[string]
    foreach ($blockMatch in [regex]::Matches($Html, "<(?:p|li)\b[^>]*>.*?</(?:p|li)>", [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        $block = $blockMatch.Value
        $text = Remove-Html $block
        $include = $text -match "Bloodline Spells|Conflux Spell|Revelation Spells|hex cantrip|School Spells"
        if (-not $include) {
            continue
        }
        foreach ($spellName in Get-SpellNamesFromUuidText $block) {
            if (-not $names.Contains($spellName)) {
                $names.Add($spellName)
            }
        }
    }
    return @($names)
}

function Get-KeyAbilityFromFeature {
    param([Parameter(Mandatory = $false)][object]$Feature)

    $keyOptions = @(Get-NestedValue -Object $Feature -Path @("system", "subfeatures", "keyOptions"))
    foreach ($keyOption in $keyOptions) {
        $ability = ConvertTo-Ability "$keyOption"
        if ($null -ne $ability) {
            return $ability
        }
    }
    return $null
}

function ConvertTo-IntegerSlotValue {
    param([Parameter(Mandatory = $false)][string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value) -or $Value.Trim() -eq "-") {
        return 0
    }
    $match = [regex]::Match($Value, "\d+")
    if ($match.Success) {
        return [int]$match.Value
    }
    return 0
}

function ConvertTo-SlotParts {
    param([Parameter(Mandatory = $false)][string]$Value)

    $cleaned = (Remove-Html $Value).Trim()
    if ($cleaned.Contains("+")) {
        return @($cleaned.Split("+") | ForEach-Object { ConvertTo-IntegerSlotValue $_ })
    }
    return @(ConvertTo-IntegerSlotValue $cleaned)
}

function Get-CellsFromRow {
    param([Parameter(Mandatory = $true)][string]$RowHtml)

    $cells = New-Object System.Collections.Generic.List[string]
    foreach ($cellMatch in [regex]::Matches($RowHtml, "<t[dh]\b[^>]*>(.*?)</t[dh]>", [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        $cells.Add((Remove-Html $cellMatch.Groups[1].Value))
    }
    return @($cells)
}

function Get-SpellSlotTable {
    param(
        [Parameter(Mandatory = $true)][string]$ClassId,
        [Parameter(Mandatory = $true)][string]$ClassName,
        [Parameter(Mandatory = $true)][string]$PageHtml
    )

    foreach ($tableMatch in [regex]::Matches($PageHtml, "<table\b[^>]*>.*?</table>", [System.Text.RegularExpressions.RegexOptions]::Singleline)) {
        $tableHtml = $tableMatch.Value
        if ($tableHtml -notmatch "Cantrips" -or $tableHtml -notmatch "<th>1st</th>" -or $tableHtml -notmatch "<th>10th</th>") {
            continue
        }

        $rows = @([regex]::Matches($tableHtml, "<tr\b[^>]*>.*?</tr>", [System.Text.RegularExpressions.RegexOptions]::Singleline))
        if ($rows.Count -lt 2) {
            continue
        }
        $headers = @(Get-CellsFromRow $rows[0].Value)
        if ($headers.Count -lt 3 -or $headers[0] -ne "Your Level") {
            continue
        }

        $rankByIndex = @{}
        for ($index = 1; $index -lt $headers.Count; $index++) {
            $header = $headers[$index]
            if ($header -eq "Cantrips") {
                $rankByIndex[$index] = 0
            }
            elseif ($header -match "^(\d+)") {
                $rankByIndex[$index] = [int]$Matches[1]
            }
        }

        $single = @{}
        $splitFirst = @{}
        $splitSecond = @{}
        $hasSplitCells = $false

        for ($rowIndex = 1; $rowIndex -lt $rows.Count; $rowIndex++) {
            $cells = @(Get-CellsFromRow $rows[$rowIndex].Value)
            if ($cells.Count -lt 2 -or $cells[0] -notmatch "^\d+$") {
                continue
            }
            $level = [int]$cells[0]
            $singleRanks = @{}
            $firstRanks = @{}
            $secondRanks = @{}

            for ($cellIndex = 1; $cellIndex -lt $cells.Count; $cellIndex++) {
                if (-not $rankByIndex.ContainsKey($cellIndex)) {
                    continue
                }
                $rank = [int]$rankByIndex[$cellIndex]
                $parts = @(ConvertTo-SlotParts $cells[$cellIndex])
                if ($parts.Count -gt 1) {
                    $hasSplitCells = $true
                    if ($parts[0] -gt 0) {
                        $firstRanks["$rank"] = $parts[0]
                    }
                    if ($parts[1] -gt 0) {
                        $secondRanks["$rank"] = $parts[1]
                    }
                }
                elseif ($parts[0] -gt 0) {
                    $singleRanks["$rank"] = $parts[0]
                }
            }

            if ($singleRanks.Count -gt 0) {
                $single["$level"] = $singleRanks
            }
            if ($firstRanks.Count -gt 0) {
                $splitFirst["$level"] = $firstRanks
            }
            if ($secondRanks.Count -gt 0) {
                $splitSecond["$level"] = $secondRanks
            }
        }

        if ($hasSplitCells) {
            return [PSCustomObject]@{
                split = $true
                primary = $splitFirst
                secondary = $splitSecond
            }
        }

        return [PSCustomObject]@{
            split = $false
            primary = $single
            secondary = @{}
        }
    }

    return $null
}

function Test-BoundedSlots {
    param([Parameter(Mandatory = $true)][hashtable]$SlotsByLevel)

    $seenRanks = New-Object System.Collections.Generic.HashSet[int]
    for ($level = 1; $level -le 20; $level++) {
        $levelSlots = $SlotsByLevel["$level"]
        if ($null -eq $levelSlots) {
            continue
        }
        foreach ($rankKey in $levelSlots.Keys) {
            $rank = [int]$rankKey
            if ($rank -gt 0) {
                [void]$seenRanks.Add($rank)
            }
        }
        foreach ($rank in @($seenRanks)) {
            if ($rank -gt 0 -and -not $levelSlots.ContainsKey("$rank")) {
                return $true
            }
        }
    }
    return $false
}

function Get-ClassChoiceGroups {
    param(
        $ClassJson,
        $ClassId,
        $FeaturesByName,
        $FeaturesByTag
    )

    $groups = @()
    $items = @(Get-NestedValue -Object $ClassJson -Path @("system", "items"))
    if ($items.Count -eq 0 -or $null -eq $items[0]) {
        return @()
    }

    foreach ($itemProperty in $ClassJson.system.items.PSObject.Properties) {
        $item = $itemProperty.Value
        $level = Get-NestedValue -Object $item -Path @("level")
        if ($null -eq $level -or [int]$level -ne 1) {
            continue
        }
        $itemName = Get-NestedValue -Object $item -Path @("name")
        if ($null -eq $itemName -or -not $FeaturesByName.ContainsKey("$itemName")) {
            continue
        }
        $feature = $FeaturesByName["$itemName"]
        foreach ($rule in @(Get-NestedValue -Object $feature -Path @("system", "rules"))) {
            if ((Get-NestedValue -Object $rule -Path @("key")) -ne "ChoiceSet") {
                continue
            }
            $filters = @(Get-NestedValue -Object $rule -Path @("choices", "filter"))
            foreach ($filter in $filters) {
                if ("$filter" -notmatch "^item:tag:([a-z0-9-]+)$") {
                    continue
                }
                $tag = $Matches[1]
                if (-not $FeaturesByTag.ContainsKey($tag)) {
                    continue
                }
                $choices = @()
                foreach ($optionFeature in @($FeaturesByTag[$tag] | Sort-Object -Property name)) {
                    $traits = @(Get-NestedValue -Object $optionFeature -Path @("system", "traits", "value"))
                    if ($traits.Count -gt 0 -and $traits -notcontains $ClassId) {
                        continue
                    }
                    $optionName = "$($optionFeature.name)"
                    $optionSlug = ConvertTo-Slug $optionName
                    $optionLabel = $optionName -replace "^Bloodline:\s*", ""
                    $optionDescription = Get-FeatureDescription $optionFeature
                    $baseTradition = Get-FeatureTradition $optionFeature
                    $keyAbility = Get-KeyAbilityFromFeature $optionFeature
                    $grantedSpells = @(Get-GrantedSpellNames $optionDescription)
                    $focusSpells = @(Get-FocusSpellNames $optionDescription)

                    $expanded = $false
                    foreach ($optionRule in @(Get-NestedValue -Object $optionFeature -Path @("system", "rules"))) {
                        if ((Get-NestedValue -Object $optionRule -Path @("key")) -ne "ChoiceSet") {
                            continue
                        }
                        foreach ($subchoice in @(Get-NestedValue -Object $optionRule -Path @("choices"))) {
                            $subTradition = ConvertTo-Tradition "$(Get-NestedValue -Object $subchoice -Path @("value", "tradition"))"
                            $subSlug = Get-NestedValue -Object $subchoice -Path @("value", "slug")
                            if ($null -eq $subTradition -or [string]::IsNullOrWhiteSpace("$subSlug")) {
                                continue
                            }
                            $subLabel = "$subSlug".Replace("-", " ")
                            $subLabel = (Get-Culture).TextInfo.ToTitleCase($subLabel)
                            $choices += [PSCustomObject]@{
                                optionId = "class/$ClassId/$tag/$optionSlug/$(ConvertTo-Slug $subLabel)"
                                label = "$optionLabel ($subLabel)"
                                tradition = $subTradition
                                keyAbility = $keyAbility
                                grantedSpellNames = $grantedSpells
                                focusSpellNames = $focusSpells
                            }
                            $expanded = $true
                        }
                    }

                    if (-not $expanded) {
                        $choices += [PSCustomObject]@{
                            optionId = "class/$ClassId/$tag/$optionSlug"
                            label = $optionLabel
                            tradition = $baseTradition
                            keyAbility = $keyAbility
                            grantedSpellNames = $grantedSpells
                            focusSpellNames = $focusSpells
                        }
                    }
                }

                if ($choices.Count -gt 0) {
                    $groups += [PSCustomObject]@{
                        id = $tag
                        label = "$itemName"
                        optionType = "CLASS_FEATURE"
                        required = $true
                        choices = @($choices)
                    }
                }
            }
        }
    }
    return @($groups)
}

if (-not (Test-Path -LiteralPath $FoundryPf2ePacksDir)) {
    throw "PF2e packs directory not found: $FoundryPf2ePacksDir"
}
if (-not (Test-Path -LiteralPath $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

$classesDir = Join-Path $FoundryPf2ePacksDir "classes"
$featuresDir = Join-Path $FoundryPf2ePacksDir "class-features"
$journalPath = Join-Path $FoundryPf2ePacksDir "journals/classes.json"
foreach ($requiredPath in @($classesDir, $featuresDir, $journalPath)) {
    if (-not (Test-Path -LiteralPath $requiredPath)) {
        throw "Required PF2e data path not found: $requiredPath"
    }
}

$featuresByName = @{}
$featuresByTag = @{}
foreach ($featureFile in Get-ChildItem -LiteralPath $featuresDir -Filter *.json -File) {
    $feature = Read-JsonFile $featureFile.FullName
    $featuresByName["$($feature.name)"] = $feature
    foreach ($tag in @(Get-NestedValue -Object $feature -Path @("system", "traits", "otherTags"))) {
        if ([string]::IsNullOrWhiteSpace("$tag")) {
            continue
        }
        if (-not $featuresByTag.ContainsKey("$tag")) {
            $featuresByTag["$tag"] = New-Object System.Collections.Generic.List[object]
        }
        $featuresByTag["$tag"].Add($feature)
    }
}

$journal = Read-JsonFile $journalPath
$journalPagesByName = @{}
foreach ($page in @(Get-NestedValue -Object $journal -Path @("pages"))) {
    $journalPagesByName["$($page.name)"] = $page
}

$definitions = @()
foreach ($classFile in Get-ChildItem -LiteralPath $classesDir -Filter *.json -File | Sort-Object -Property Name) {
    $classJson = Read-JsonFile $classFile.FullName
    if ((Get-NestedValue -Object $classJson -Path @("type")) -ne "class") {
        continue
    }
    $spellcasting = Get-NestedValue -Object $classJson -Path @("system", "spellcasting")
    if ($null -eq $spellcasting -or [int]$spellcasting -le 0) {
        continue
    }

    $classId = [System.IO.Path]::GetFileNameWithoutExtension($classFile.Name).ToLowerInvariant()
    $className = "$($classJson.name)"
    $page = $journalPagesByName[$className]
    if ($null -eq $page) {
        continue
    }
    $pageHtml = "$((Get-NestedValue -Object $page -Path @("text", "content")))"
    $slotTable = Get-SpellSlotTable -ClassId $classId -ClassName $className -PageHtml $pageHtml
    if ($null -eq $slotTable) {
        continue
    }

    $keyAbilityOptions = New-Object System.Collections.Generic.List[string]
    foreach ($rawAbility in @(Get-NestedValue -Object $classJson -Path @("system", "keyAbility", "value"))) {
        $ability = ConvertTo-Ability "$rawAbility"
        if ($null -ne $ability -and -not $keyAbilityOptions.Contains($ability)) {
            $keyAbilityOptions.Add($ability)
        }
    }

    $choiceGroups = @(Get-ClassChoiceGroups -ClassJson $classJson -ClassId $classId -FeaturesByName $featuresByName -FeaturesByTag $featuresByTag)
    foreach ($choice in @($choiceGroups | ForEach-Object { $_.choices } | ForEach-Object { $_ })) {
        if ($null -ne $choice.keyAbility -and -not $keyAbilityOptions.Contains($choice.keyAbility)) {
            $keyAbilityOptions.Add($choice.keyAbility)
        }
    }
    if ($keyAbilityOptions.Count -eq 0) {
        $keyAbilityOptions.Add("INTELLIGENCE")
    }

    $spellcastingFeatureDescriptions = New-Object System.Collections.Generic.List[string]
    $baseTradition = $null
    foreach ($itemProperty in $classJson.system.items.PSObject.Properties) {
        $item = $itemProperty.Value
        $itemName = "$($item.name)"
        if ($itemName -match "Spellcasting" -and $featuresByName.ContainsKey($itemName)) {
            $feature = $featuresByName[$itemName]
            $spellcastingFeatureDescriptions.Add((Get-FeatureDescription $feature))
            $featureTradition = Get-FeatureTradition $feature
            if ($null -ne $featureTradition) {
                $baseTradition = $featureTradition
            }
        }
    }
    $spellcastingDescription = [string]::Join(" ", $spellcastingFeatureDescriptions)
    if ($null -eq $baseTradition) {
        $baseTradition = Get-TextTradition $spellcastingDescription
    }
    $style = if ((Remove-Html $spellcastingDescription) -match "spell repertoire|must know spells") { "SPONTANEOUS" } else { "PREPARED" }
    $bounded = Test-BoundedSlots $slotTable.primary

    $tracks = @()
    if ($slotTable.split) {
        $tracks += [PSCustomObject]@{
            trackKey = "primary"
            displayName = $className
            progressionType = "ANIMIST_PREPARED"
            castingStyle = "PREPARED"
            tradition = $baseTradition
            slotProgressionKey = "class/$classId/primary"
            slotsByLevel = $slotTable.primary
        }
        $tracks += [PSCustomObject]@{
            trackKey = "apparition"
            displayName = "Apparition"
            progressionType = "ANIMIST_APPARITION_SPONTANEOUS"
            castingStyle = "SPONTANEOUS"
            tradition = $baseTradition
            slotProgressionKey = "class/$classId/apparition"
            slotsByLevel = $slotTable.secondary
        }
    }
    else {
        $progressionType = if ($bounded) {
            if ($style -eq "SPONTANEOUS") { "BOUNDED_SPONTANEOUS" } else { "BOUNDED_PREPARED" }
        }
        else {
            if ($style -eq "SPONTANEOUS") { "FULL_SPONTANEOUS" } else { "FULL_PREPARED" }
        }
        $tracks += [PSCustomObject]@{
            trackKey = "primary"
            displayName = $className
            progressionType = $progressionType
            castingStyle = $style
            tradition = $baseTradition
            slotProgressionKey = "class/$classId/primary"
            slotsByLevel = $slotTable.primary
        }
    }

    $definitions += [PSCustomObject]@{
        id = $classId
        name = $className
        defaultKeyAbility = $keyAbilityOptions[0]
        keyAbilityOptions = @($keyAbilityOptions)
        baseTradition = $baseTradition
        primaryTracks = @($tracks)
        choiceGroups = $choiceGroups
    }
}

if ($definitions.Count -eq 0) {
    throw "No spellcasting class definitions generated."
}

$datasetVersion = Get-Date -Format "yyyyMMdd-HHmmss"
$normalized = [PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    generatedAtUtc = (Get-Date).ToUniversalTime().ToString("o")
    classCount = $definitions.Count
    classes = @($definitions | Sort-Object -Property name)
}

$normalizedPath = Join-Path $OutputDir "class-spellcasting.normalized.json"
$changelogPath = Join-Path $OutputDir "class-spellcasting.changelog.json"
$normalized | ConvertTo-Json -Depth 100 | Set-Content -LiteralPath $normalizedPath -Encoding UTF8
([PSCustomObject]@{
    datasetVersion = $datasetVersion
    sourceCommit = $SourceCommit
    added = $definitions.Count
    changed = 0
    removed = 0
}) | ConvertTo-Json -Depth 20 | Set-Content -LiteralPath $changelogPath -Encoding UTF8

Write-Host "Generated class spellcasting dataset:"
Write-Host "  - $normalizedPath"
Write-Host "  - $changelogPath"
