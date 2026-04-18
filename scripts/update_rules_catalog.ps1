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
$lookupPath = Join-Path $importOutDir "rules.lookup.normalized.json"

if (-not (Test-Path -LiteralPath $normalizedPath)) {
    throw "Importer did not generate normalized rules catalog: $normalizedPath"
}
if (-not (Test-Path -LiteralPath $lookupPath)) {
    throw "Importer did not generate rules lookup dataset: $lookupPath"
}

$normalized = Get-Content -LiteralPath $normalizedPath -Raw -Encoding UTF8 | ConvertFrom-Json
$optionCount = [int]$normalized.catalogCounts.totalOptions
if ($optionCount -lt $MinOptionCount) {
    throw "Rules option count ($optionCount) is below MinOptionCount ($MinOptionCount). Aborting asset update."
}
$lookup = Get-Content -LiteralPath $lookupPath -Raw -Encoding UTF8 | ConvertFrom-Json

Copy-Item -LiteralPath $normalizedPath -Destination (Join-Path $assetDir "rules.catalog.normalized.json") -Force
Copy-Item -LiteralPath $lookupPath -Destination (Join-Path $assetDir "rules.lookup.normalized.json") -Force
if (Test-Path -LiteralPath $attributionPath) {
    Copy-Item -LiteralPath $attributionPath -Destination (Join-Path $assetDir "rules.catalog.attribution.json") -Force
}
if (Test-Path -LiteralPath $changelogPath) {
    Copy-Item -LiteralPath $changelogPath -Destination (Join-Path $assetDir "rules.catalog.changelog.json") -Force
}

Write-Host "Rules catalog update complete."
Write-Host "  Options: $optionCount"
Write-Host "  Lookup traits: $($lookup.counts.traitsResolved)"
Write-Host "  Lookup conditions: $($lookup.counts.conditionsResolved)"
Write-Host "  Source commit: $SourceCommit"
Write-Host "  Asset dir: $assetDir"
