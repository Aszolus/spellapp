param(
    [Parameter(Mandatory = $true)]
    [string]$FoundryPf2ePacksDir,

    [Parameter(Mandatory = $true)]
    [string]$SourceCommit,

    [int]$MinOptionCount = 200
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$importerScript = Join-Path $repoRoot "tools/importer/import_rules_catalog.ps1"
$importOutDir = Join-Path $repoRoot "tools/importer/out"
$assetDir = Join-Path $repoRoot "app/src/main/assets"

if (-not (Test-Path -LiteralPath $importerScript)) {
    throw "Importer script not found: $importerScript"
}

if (-not (Test-Path -LiteralPath $assetDir)) {
    New-Item -ItemType Directory -Path $assetDir | Out-Null
}

& $importerScript `
    -PacksDir $FoundryPf2ePacksDir `
    -OutputDir $importOutDir `
    -SourceCommit $SourceCommit

$normalizedPath = Join-Path $importOutDir "rules.catalog.normalized.json"
$attributionPath = Join-Path $importOutDir "rules.catalog.attribution.json"
$changelogPath = Join-Path $importOutDir "rules.catalog.changelog.json"
$referenceIndexPath = Join-Path $importOutDir "rules.reference.index.json"
$referenceShardPaths = @(
    Get-ChildItem -LiteralPath $importOutDir -Filter "rules.reference.*.json.gz" -File -ErrorAction SilentlyContinue |
        Sort-Object -Property Name
)
$localizationPath = Join-Path $importOutDir "foundry.localization.en.flat.json"

if (-not (Test-Path -LiteralPath $normalizedPath)) {
    throw "Importer did not generate normalized rules catalog: $normalizedPath"
}
if (-not (Test-Path -LiteralPath $referenceIndexPath)) {
    throw "Importer did not generate rules reference index: $referenceIndexPath"
}
if ($referenceShardPaths.Count -eq 0) {
    throw "Importer did not generate any rules reference shards in: $importOutDir"
}
if (-not (Test-Path -LiteralPath $localizationPath)) {
    throw "Importer did not generate Foundry localization dataset: $localizationPath"
}

$normalized = Get-Content -LiteralPath $normalizedPath -Raw -Encoding UTF8 | ConvertFrom-Json
$optionCount = [int]$normalized.catalogCounts.totalOptions
if ($optionCount -lt $MinOptionCount) {
    throw "Rules option count ($optionCount) is below MinOptionCount ($MinOptionCount). Aborting asset update."
}
$referenceIndex = Get-Content -LiteralPath $referenceIndexPath -Raw -Encoding UTF8 | ConvertFrom-Json
$localization = Get-Content -LiteralPath $localizationPath -Raw -Encoding UTF8 | ConvertFrom-Json

Copy-Item -LiteralPath $normalizedPath -Destination (Join-Path $assetDir "rules.catalog.normalized.json") -Force
Copy-Item -LiteralPath $referenceIndexPath -Destination (Join-Path $assetDir "rules.reference.index.json") -Force
foreach ($referenceShardPath in $referenceShardPaths) {
    Copy-Item -LiteralPath $referenceShardPath.FullName -Destination (Join-Path $assetDir $referenceShardPath.Name) -Force
}
Copy-Item -LiteralPath $localizationPath -Destination (Join-Path $assetDir "foundry.localization.en.flat.json") -Force
if (Test-Path -LiteralPath $attributionPath) {
    Copy-Item -LiteralPath $attributionPath -Destination (Join-Path $assetDir "rules.catalog.attribution.json") -Force
}
if (Test-Path -LiteralPath $changelogPath) {
    Copy-Item -LiteralPath $changelogPath -Destination (Join-Path $assetDir "rules.catalog.changelog.json") -Force
}

Write-Host "Rules catalog update complete."
Write-Host "  Options: $optionCount"
Write-Host "  Trait definitions: $($referenceIndex.counts.traitDefinitions)"
Write-Host "  Reference entries: $($referenceIndex.counts.referenceEntries)"
Write-Host "  Reference shards: $($referenceShardPaths.Count)"
Write-Host "  Localization entries: $($localization.counts.entries)"
Write-Host "  Source commit: $SourceCommit"
Write-Host "  Asset dir: $assetDir"
