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

if (-not (Test-Path -LiteralPath $normalizedPath)) {
    throw "Importer did not generate normalized rules catalog: $normalizedPath"
}

$normalized = Get-Content -LiteralPath $normalizedPath -Raw | ConvertFrom-Json
$optionCount = [int]$normalized.catalogCounts.totalOptions
if ($optionCount -lt $MinOptionCount) {
    throw "Rules option count ($optionCount) is below MinOptionCount ($MinOptionCount). Aborting asset update."
}

Copy-Item -LiteralPath $normalizedPath -Destination (Join-Path $assetDir "rules.catalog.normalized.json") -Force
if (Test-Path -LiteralPath $attributionPath) {
    Copy-Item -LiteralPath $attributionPath -Destination (Join-Path $assetDir "rules.catalog.attribution.json") -Force
}
if (Test-Path -LiteralPath $changelogPath) {
    Copy-Item -LiteralPath $changelogPath -Destination (Join-Path $assetDir "rules.catalog.changelog.json") -Force
}

Write-Host "Rules catalog update complete."
Write-Host "  Options: $optionCount"
Write-Host "  Source commit: $SourceCommit"
Write-Host "  Asset dir: $assetDir"
