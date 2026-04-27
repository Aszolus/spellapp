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

function ConvertTo-Count {
    param([Parameter(Mandatory = $false)][string]$Value)

    if ([string]::IsNullOrWhiteSpace($Value)) {
        return 0
    }
    $normalized = $Value.Trim().ToLowerInvariant()
    if ($normalized -match "^\d+$") {
        return [int]$normalized
    }
    switch ($normalized) {
        "one" { return 1 }
        "two" { return 2 }
        "three" { return 3 }
        "four" { return 4 }
        "five" { return 5 }
        "six" { return 6 }
        "seven" { return 7 }
        "eight" { return 8 }
        "nine" { return 9 }
        "ten" { return 10 }
        default { return 0 }
    }
}

function Copy-RankCounts {
    param([Parameter(Mandatory = $true)][hashtable]$Counts)

    $copy = @{}
    foreach ($key in $Counts.Keys) {
        $copy["$key"] = [int]$Counts[$key]
    }
    return $copy
}

function Add-RankCount {
    param(
        [Parameter(Mandatory = $true)][hashtable]$Counts,
        [Parameter(Mandatory = $true)][int]$Rank,
        [Parameter(Mandatory = $true)][int]$Delta
    )

    $key = "$Rank"
    $current = 0
    if ($Counts.ContainsKey($key)) {
        $current = [int]$Counts[$key]
    }
    $next = $current + $Delta
    if ($next -le 0) {
        if ($Counts.ContainsKey($key)) {
            $Counts.Remove($key)
        }
        return
    }
    $Counts[$key] = $next
}

function Get-RankCountTotal {
    param([Parameter(Mandatory = $true)][hashtable]$Counts)

    $total = 0
    foreach ($key in $Counts.Keys) {
        $total += [int]$Counts[$key]
    }
    return $total
}

function Get-FeatureBySlug {
    param(
        [Parameter(Mandatory = $true)][hashtable]$FeaturesBySlug,
        [Parameter(Mandatory = $true)][string]$Slug
    )

    if ($FeaturesBySlug.ContainsKey($Slug)) {
        return $FeaturesBySlug[$Slug]
    }
    return $null
}

function Get-RepertoireDescription {
    param(
        [Parameter(Mandatory = $true)][string]$ClassId,
        [Parameter(Mandatory = $true)][hashtable]$FeaturesBySlug
    )

    $feature = Get-FeatureBySlug -FeaturesBySlug $FeaturesBySlug -Slug "spell-repertoire-$ClassId"
    if ($null -ne $feature) {
        return [PSCustomObject]@{
            source = "$($feature.name)"
            text = Remove-Html (Get-FeatureDescription $feature)
        }
    }
    return $null
}

function Get-RepertoireSeed {
    param(
        [Parameter(Mandatory = $true)][string]$ClassId,
        [Parameter(Mandatory = $true)][object]$ClassJson,
        [Parameter(Mandatory = $true)][hashtable]$FeaturesByName,
        [Parameter(Mandatory = $true)][hashtable]$FeaturesBySlug
    )

    $description = Get-RepertoireDescription -ClassId $ClassId -FeaturesBySlug $FeaturesBySlug
    if ($null -eq $description) {
        return $null
    }

    $text = "$($description.text)"
    $counts = @{}
    $rankBonus = 0
    $cantripBonus = 0
    $sourceNotes = New-Object System.Collections.Generic.List[string]
    $sourceNotes.Add($description.source)

    $initialPattern = "At 1st level, you learn ([A-Za-z0-9]+) 1st-rank .*? and ([A-Za-z0-9]+) .*?cantrips"
    $initial = [regex]::Match($text, $initialPattern, [System.Text.RegularExpressions.RegexOptions]::IgnoreCase)
    if ($initial.Success) {
        $rankOne = ConvertTo-Count $initial.Groups[1].Value
        $cantrips = ConvertTo-Count $initial.Groups[2].Value
        if ($rankOne -gt 0) {
            $counts["1"] = $rankOne
        }
        if ($cantrips -gt 0) {
            $counts["0"] = $cantrips
        }
    }

    if ($text -match "additional spell and cantrip from your bloodline") {
        $rankBonus = [Math]::Max($rankBonus, 1)
        $cantripBonus = [Math]::Max($cantripBonus, 1)
        $sourceNotes.Add("Bloodline granted repertoire")
    }

    $psychicBonus = [regex]::Match(
        $text,
        "additional 1st-rank spell and ([A-Za-z0-9]+) cantrips",
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if ($psychicBonus.Success) {
        $rankBonus = [Math]::Max($rankBonus, 1)
        $cantripBonus = [Math]::Max($cantripBonus, (ConvertTo-Count $psychicBonus.Groups[1].Value))
        $sourceNotes.Add("Conscious mind granted repertoire")
    }

    if ($rankBonus -eq 0) {
        foreach ($itemProperty in $ClassJson.system.items.PSObject.Properties) {
            $item = $itemProperty.Value
            $itemName = "$($item.name)"
            if (-not $FeaturesByName.ContainsKey($itemName)) {
                continue
            }
            $feature = $FeaturesByName[$itemName]
            $featureText = Remove-Html (Get-FeatureDescription $feature)
            if ($featureText -match "Granted Spells" -and
                $featureText -match "At 1st level, you gain a cantrip and a 1st-rank spell" -and
                $featureText -match "spell repertoire") {
                $rankBonus = 1
                $cantripBonus = [Math]::Max($cantripBonus, 1)
                $sourceNotes.Add("$itemName granted repertoire")
                break
            }
        }
    }

    return [PSCustomObject]@{
        counts = $counts
        rankBonus = $rankBonus
        cantripBonus = $cantripBonus
        source = [string]::Join("; ", @($sourceNotes))
    }
}

function Get-RepertoireCountsByLevel {
    param(
        [Parameter(Mandatory = $true)][string]$ClassId,
        [Parameter(Mandatory = $true)][object]$ClassJson,
        [Parameter(Mandatory = $true)][hashtable]$SlotsByLevel,
        [Parameter(Mandatory = $true)][hashtable]$FeaturesByName,
        [Parameter(Mandatory = $true)][hashtable]$FeaturesBySlug
    )

    $seed = Get-RepertoireSeed `
        -ClassId $ClassId `
        -ClassJson $ClassJson `
        -FeaturesByName $FeaturesByName `
        -FeaturesBySlug $FeaturesBySlug
    $countsByLevel = @{}
    $totalsByLevel = @{}
    $source = "Spell slot table"
    if ($null -ne $seed) {
        $source = $seed.source
    }

    if ($null -ne $seed -and -not (Test-BoundedSlots $SlotsByLevel)) {
        $levelOneSlots = $SlotsByLevel["1"]
        $seedRankOne = if ($seed.counts.ContainsKey("1")) { [int]$seed.counts["1"] } else { 0 }
        $levelOneRankOneSlots = if ($null -ne $levelOneSlots -and $levelOneSlots.ContainsKey("1")) {
            [int]$levelOneSlots["1"]
        }
        else {
            0
        }
        $rankBonusIncludedInSlots = [int]$seed.rankBonus -gt 0 -and
            $levelOneRankOneSlots -ge ($seedRankOne + [int]$seed.rankBonus)

        for ($level = 1; $level -le 20; $level++) {
            $currentSlots = $SlotsByLevel["$level"]
            if ($null -eq $currentSlots) {
                continue
            }
            $levelCounts = @{}
            foreach ($rankKey in @($currentSlots.Keys)) {
                $rank = [int]$rankKey
                if ($rank -eq 0) {
                    $seedCantrips = if ($seed.counts.ContainsKey("0")) { [int]$seed.counts["0"] } else { 0 }
                    $expectedCantrips = [Math]::Max([int]$currentSlots[$rankKey], $seedCantrips + [int]$seed.cantripBonus)
                    if ($expectedCantrips -gt 0) {
                        $levelCounts["0"] = $expectedCantrips
                    }
                }
                else {
                    $bonus = if ($rankBonusIncludedInSlots) { 0 } else { [int]$seed.rankBonus }
                    $levelCounts["$rank"] = [int]$currentSlots[$rankKey] + $bonus
                }
            }
            $countsByLevel["$level"] = $levelCounts
            $totalsByLevel["$level"] = Get-RankCountTotal $levelCounts
        }

        return [PSCustomObject]@{
            countsByLevel = $countsByLevel
            totalsByLevel = $totalsByLevel
            source = $source
        }
    }

    $running = @{}
    if ($null -ne $seed) {
        $running = Copy-RankCounts $seed.counts
    }
    else {
        $levelOneSlots = $SlotsByLevel["1"]
        if ($null -ne $levelOneSlots) {
            $running = Copy-RankCounts $levelOneSlots
        }
    }

    for ($level = 1; $level -le 20; $level++) {
        $currentSlots = $SlotsByLevel["$level"]
        if ($null -eq $currentSlots) {
            continue
        }

        if ($level -gt 1) {
            $previousSlots = $SlotsByLevel["$($level - 1)"]
            if ($null -eq $previousSlots) {
                $previousSlots = @{}
            }
            $rankKeys = New-Object System.Collections.Generic.HashSet[string]
            foreach ($key in @($currentSlots.Keys)) {
                if ("$key" -ne "0") {
                    [void]$rankKeys.Add("$key")
                }
            }
            foreach ($key in @($previousSlots.Keys)) {
                if ("$key" -ne "0") {
                    [void]$rankKeys.Add("$key")
                }
            }
            foreach ($rankKey in @($rankKeys)) {
                $current = if ($currentSlots.ContainsKey($rankKey)) { [int]$currentSlots[$rankKey] } else { 0 }
                $previous = if ($previousSlots.ContainsKey($rankKey)) { [int]$previousSlots[$rankKey] } else { 0 }
                $delta = $current - $previous
                if ($delta -ne 0) {
                    Add-RankCount -Counts $running -Rank ([int]$rankKey) -Delta $delta
                }
            }
        }

        $levelCounts = Copy-RankCounts $running
        if ($null -ne $seed) {
            if ([int]$seed.cantripBonus -gt 0) {
                Add-RankCount -Counts $levelCounts -Rank 0 -Delta ([int]$seed.cantripBonus)
            }
            if ([int]$seed.rankBonus -gt 0) {
                foreach ($rankKey in @($currentSlots.Keys)) {
                    if ("$rankKey" -ne "0") {
                        Add-RankCount -Counts $levelCounts -Rank ([int]$rankKey) -Delta ([int]$seed.rankBonus)
                    }
                }
            }
        }
        $countsByLevel["$level"] = $levelCounts
        $totalsByLevel["$level"] = Get-RankCountTotal $levelCounts
    }

    return [PSCustomObject]@{
        countsByLevel = $countsByLevel
        totalsByLevel = $totalsByLevel
        source = $source
    }
}

function Get-SignatureAllowanceRules {
    param(
        [Parameter(Mandatory = $true)][string]$TrackKey,
        [Parameter(Mandatory = $true)][object]$ClassJson,
        [Parameter(Mandatory = $true)][hashtable]$SlotsByLevel,
        [Parameter(Mandatory = $false)][string]$SpellcastingDescription
    )

    $rules = @()
    $unlimitedLevel = $null
    $signatureLevel = $null
    foreach ($itemProperty in $ClassJson.system.items.PSObject.Properties) {
        $item = $itemProperty.Value
        $itemName = "$($item.name)"
        if ($itemName -eq "Unlimited Signature Spells") {
            $unlimitedLevel = [int]$item.level
        }
        elseif ($itemName -eq "Signature Spells") {
            $signatureLevel = [int]$item.level
        }
    }

    $spellcastingText = Remove-Html $SpellcastingDescription
    if ($null -ne $unlimitedLevel -or $spellcastingText -match "All your .* spells are signature spells") {
        $rules += [PSCustomObject]@{
            trackKey = $TrackKey
            kind = "SIGNATURE_SPELLS"
            label = "Signature spells"
            policy = "ALL_KNOWN"
            totalsByLevel = @{}
            countsByLevel = @{}
            source = if ($null -ne $unlimitedLevel) { "Unlimited Signature Spells" } else { "Class spellcasting" }
            note = "All known spells on this track are signature spells."
        }
    }
    elseif ($null -ne $signatureLevel) {
        $totals = @{}
        for ($level = 1; $level -le 20; $level++) {
            $slots = $SlotsByLevel["$level"]
            if ($null -eq $slots) {
                continue
            }
            if ($level -lt $signatureLevel) {
                $totals["$level"] = 0
                continue
            }
            $rankCount = 0
            foreach ($rankKey in @($slots.Keys)) {
                if ("$rankKey" -ne "0" -and [int]$slots[$rankKey] -gt 0) {
                    $rankCount += 1
                }
            }
            $totals["$level"] = $rankCount
        }
        $rules += [PSCustomObject]@{
            trackKey = $TrackKey
            kind = "SIGNATURE_SPELLS"
            label = "Signature spells"
            policy = "CAP"
            totalsByLevel = $totals
            countsByLevel = @{}
            source = "Signature Spells"
            note = "Choose one signature spell for each spell rank you can cast."
        }
    }
    return @($rules)
}

function Get-SpellbookAllowanceRules {
    param(
        [Parameter(Mandatory = $true)][string]$TrackKey,
        [Parameter(Mandatory = $true)][string]$ClassName,
        [Parameter(Mandatory = $true)][string]$SpellcastingDescription
    )

    $text = Remove-Html $SpellcastingDescription
    if ($text -notmatch "Spellbook") {
        return @()
    }
    $match = [regex]::Match(
        $text,
        "contains your choice of ([A-Za-z0-9]+) .*?cantrips and ([A-Za-z0-9]+) 1st-rank",
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if (-not $match.Success) {
        return @()
    }
    $cantrips = ConvertTo-Count $match.Groups[1].Value
    $rankOne = ConvertTo-Count $match.Groups[2].Value
    if ($cantrips -le 0 -and $rankOne -le 0) {
        return @()
    }

    $counts = @{}
    $totals = @{}
    for ($level = 1; $level -le 20; $level++) {
        $levelCounts = @{}
        if ($cantrips -gt 0) {
            $levelCounts["0"] = $cantrips
        }
        if ($rankOne -gt 0) {
            $levelCounts["1"] = $rankOne
        }
        $counts["$level"] = $levelCounts
        $totals["$level"] = $cantrips + $rankOne + (2 * ($level - 1))
    }

    return @([PSCustomObject]@{
        trackKey = $TrackKey
        kind = "SPELLBOOK_MINIMUM"
        label = "Spellbook minimum"
        policy = "MINIMUM"
        countsByLevel = $counts
        totalsByLevel = $totals
        source = "$ClassName Spellcasting"
        note = "Level-up spellbook additions can be any rank you can cast, so only total minimum is strict after 1st level."
    })
}

function Get-FamiliarAllowanceRules {
    param(
        [Parameter(Mandatory = $true)][string]$TrackKey,
        [Parameter(Mandatory = $true)][object]$ClassJson,
        [Parameter(Mandatory = $true)][hashtable]$FeaturesByName
    )

    $feature = $null
    foreach ($itemProperty in $ClassJson.system.items.PSObject.Properties) {
        $item = $itemProperty.Value
        $itemName = "$($item.name)"
        if (-not $FeaturesByName.ContainsKey($itemName)) {
            continue
        }
        $candidate = $FeaturesByName[$itemName]
        $candidateText = Remove-Html (Get-FeatureDescription $candidate)
        if ($candidateText -match "familiar starts off knowing") {
            $feature = $candidate
            break
        }
    }
    if ($null -eq $feature) {
        return @()
    }
    $text = Remove-Html (Get-FeatureDescription $feature)
    $match = [regex]::Match(
        $text,
        "starts off knowing ([A-Za-z0-9]+) cantrips, ([A-Za-z0-9]+) 1st-rank spells, and ([A-Za-z0-9]+) additional spell",
        [System.Text.RegularExpressions.RegexOptions]::IgnoreCase
    )
    if (-not $match.Success) {
        return @()
    }
    $cantrips = ConvertTo-Count $match.Groups[1].Value
    $rankOne = ConvertTo-Count $match.Groups[2].Value
    $patron = ConvertTo-Count $match.Groups[3].Value
    $counts = @{}
    $totals = @{}
    for ($level = 1; $level -le 20; $level++) {
        $levelCounts = @{}
        if ($cantrips -gt 0) {
            $levelCounts["0"] = $cantrips
        }
        if ($rankOne -gt 0) {
            $levelCounts["1"] = $rankOne + $patron
        }
        $counts["$level"] = $levelCounts
        $totals["$level"] = $cantrips + $rankOne + $patron + (2 * ($level - 1))
    }
    return @([PSCustomObject]@{
        trackKey = $TrackKey
        kind = "FAMILIAR_MINIMUM"
        label = "Familiar minimum"
        policy = "MINIMUM"
        countsByLevel = $counts
        totalsByLevel = $totals
        source = "$($feature.name)"
        note = "Level-up familiar spells can be any rank you can cast, so only total minimum is strict after 1st level."
    })
}

function Get-TrackAllowanceRules {
    param(
        [Parameter(Mandatory = $true)][string]$TrackKey,
        [Parameter(Mandatory = $true)][string]$ClassId,
        [Parameter(Mandatory = $true)][string]$ClassName,
        [Parameter(Mandatory = $true)][string]$CastingStyle,
        [Parameter(Mandatory = $true)][string]$ProgressionType,
        [Parameter(Mandatory = $true)][hashtable]$SlotsByLevel,
        [Parameter(Mandatory = $true)][object]$ClassJson,
        [Parameter(Mandatory = $true)][hashtable]$FeaturesByName,
        [Parameter(Mandatory = $true)][hashtable]$FeaturesBySlug,
        [Parameter(Mandatory = $false)][string]$SpellcastingDescription
    )

    $rules = @()
    if ($CastingStyle -eq "PREPARED") {
        $rules += [PSCustomObject]@{
            trackKey = $TrackKey
            kind = "PREPARED_SLOTS"
            label = "Prepared"
            policy = "CAP"
            countsByLevel = $SlotsByLevel
            totalsByLevel = @{}
            source = "$ClassName spell slots"
            note = $null
        }
        $rules += @(Get-SpellbookAllowanceRules -TrackKey $TrackKey -ClassName $ClassName -SpellcastingDescription $SpellcastingDescription)
        $rules += @(Get-FamiliarAllowanceRules -TrackKey $TrackKey -ClassJson $ClassJson -FeaturesByName $FeaturesByName)
    }
    else {
        $repertoire = Get-RepertoireCountsByLevel `
            -ClassId $ClassId `
            -ClassJson $ClassJson `
            -SlotsByLevel $SlotsByLevel `
            -FeaturesByName $FeaturesByName `
            -FeaturesBySlug $FeaturesBySlug
        $policy = if ($ProgressionType -eq "FULL_SPONTANEOUS") { "CAP" } else { "WARNING_ONLY" }
        $rules += [PSCustomObject]@{
            trackKey = $TrackKey
            kind = "REPERTOIRE"
            label = "Repertoire"
            policy = $policy
            countsByLevel = $repertoire.countsByLevel
            totalsByLevel = $repertoire.totalsByLevel
            source = $repertoire.source
            note = if ($policy -eq "WARNING_ONLY") { "Bounded or granted repertoires can shift by feature choices; treat this as guidance." } else { $null }
        }
        $rules += @(Get-SignatureAllowanceRules `
                -TrackKey $TrackKey `
                -ClassJson $ClassJson `
                -SlotsByLevel $SlotsByLevel `
                -SpellcastingDescription $SpellcastingDescription)
    }
    return @($rules)
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
$featuresBySlug = @{}
foreach ($featureFile in Get-ChildItem -LiteralPath $featuresDir -Filter *.json -File) {
    $feature = Read-JsonFile $featureFile.FullName
    $featuresByName["$($feature.name)"] = $feature
    $featuresBySlug[[System.IO.Path]::GetFileNameWithoutExtension($featureFile.Name).ToLowerInvariant()] = $feature
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
        $primaryProgressionType = "ANIMIST_PREPARED"
        $apparitionProgressionType = "ANIMIST_APPARITION_SPONTANEOUS"
        $tracks += [PSCustomObject]@{
            trackKey = "primary"
            displayName = $className
            progressionType = $primaryProgressionType
            castingStyle = "PREPARED"
            tradition = $baseTradition
            slotProgressionKey = "class/$classId/primary"
            slotsByLevel = $slotTable.primary
            allowanceRules = @(Get-TrackAllowanceRules `
                    -TrackKey "primary" `
                    -ClassId $classId `
                    -ClassName $className `
                    -CastingStyle "PREPARED" `
                    -ProgressionType $primaryProgressionType `
                    -SlotsByLevel $slotTable.primary `
                    -ClassJson $classJson `
                    -FeaturesByName $featuresByName `
                    -FeaturesBySlug $featuresBySlug `
                    -SpellcastingDescription $spellcastingDescription)
        }
        $tracks += [PSCustomObject]@{
            trackKey = "apparition"
            displayName = "Apparition"
            progressionType = $apparitionProgressionType
            castingStyle = "SPONTANEOUS"
            tradition = $baseTradition
            slotProgressionKey = "class/$classId/apparition"
            slotsByLevel = $slotTable.secondary
            allowanceRules = @(Get-TrackAllowanceRules `
                    -TrackKey "apparition" `
                    -ClassId $classId `
                    -ClassName $className `
                    -CastingStyle "SPONTANEOUS" `
                    -ProgressionType $apparitionProgressionType `
                    -SlotsByLevel $slotTable.secondary `
                    -ClassJson $classJson `
                    -FeaturesByName $featuresByName `
                    -FeaturesBySlug $featuresBySlug `
                    -SpellcastingDescription $spellcastingDescription)
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
            allowanceRules = @(Get-TrackAllowanceRules `
                    -TrackKey "primary" `
                    -ClassId $classId `
                    -ClassName $className `
                    -CastingStyle $style `
                    -ProgressionType $progressionType `
                    -SlotsByLevel $slotTable.primary `
                    -ClassJson $classJson `
                    -FeaturesByName $featuresByName `
                    -FeaturesBySlug $featuresBySlug `
                    -SpellcastingDescription $spellcastingDescription)
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
