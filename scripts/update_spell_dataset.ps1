param(
    [Parameter(Mandatory = $true)]
    [string]$FoundrySpellsDir,

    [Parameter(Mandatory = $true)]
    [string]$SourceCommit,

    [int]$MinSpellCount = 1000
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$importerScript = Join-Path $repoRoot "tools/importer/import_spells.ps1"
$importOutDir = Join-Path $repoRoot "tools/importer/out"
$assetDir = Join-Path $repoRoot "app/src/main/assets"

if (-not (Test-Path -LiteralPath $importerScript)) {
    throw "Importer script not found: $importerScript"
}

if (-not (Test-Path -LiteralPath $assetDir)) {
    New-Item -ItemType Directory -Path $assetDir | Out-Null
}

& $importerScript `
    -InputDir $FoundrySpellsDir `
    -OutputDir $importOutDir `
    -SourceCommit $SourceCommit

$normalizedPath = Join-Path $importOutDir "spells.normalized.json"
$attributionPath = Join-Path $importOutDir "spells.attribution.json"
$changelogPath = Join-Path $importOutDir "spells.changelog.json"

if (-not (Test-Path -LiteralPath $normalizedPath)) {
    throw "Importer did not generate normalized dataset: $normalizedPath"
}

$normalized = Get-Content -LiteralPath $normalizedPath -Raw | ConvertFrom-Json
$spellCount = [int]$normalized.spellCount
if ($spellCount -lt $MinSpellCount) {
    throw "Spell count ($spellCount) is below MinSpellCount ($MinSpellCount). Aborting asset update."
}

Copy-Item -LiteralPath $normalizedPath -Destination (Join-Path $assetDir "spells.normalized.json") -Force
if (Test-Path -LiteralPath $attributionPath) {
    Copy-Item -LiteralPath $attributionPath -Destination (Join-Path $assetDir "spells.attribution.json") -Force
}
if (Test-Path -LiteralPath $changelogPath) {
    Copy-Item -LiteralPath $changelogPath -Destination (Join-Path $assetDir "spells.changelog.json") -Force
}

Write-Host "Dataset update complete."
Write-Host "  Spell count: $spellCount"
Write-Host "  Source commit: $SourceCommit"
Write-Host "  Asset dir: $assetDir"
