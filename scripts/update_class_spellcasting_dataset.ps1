param(
    [Parameter(Mandatory = $true)]
    [string]$FoundryPf2ePacksDir,

    [Parameter(Mandatory = $true)]
    [string]$SourceCommit,

    [int]$MinClassCount = 10
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$importerScript = Join-Path $repoRoot "tools/importer/import_class_spellcasting.ps1"
$importOutDir = Join-Path $repoRoot "tools/importer/out"
$assetDir = Join-Path $repoRoot "app/src/main/assets"

if (-not (Test-Path -LiteralPath $importerScript)) {
    throw "Importer script not found: $importerScript"
}

if (-not (Test-Path -LiteralPath $assetDir)) {
    New-Item -ItemType Directory -Path $assetDir | Out-Null
}

& $importerScript `
    -FoundryPf2ePacksDir $FoundryPf2ePacksDir `
    -OutputDir $importOutDir `
    -SourceCommit $SourceCommit

$normalizedPath = Join-Path $importOutDir "class-spellcasting.normalized.json"
$changelogPath = Join-Path $importOutDir "class-spellcasting.changelog.json"

if (-not (Test-Path -LiteralPath $normalizedPath)) {
    throw "Importer did not generate normalized dataset: $normalizedPath"
}

$normalized = Get-Content -LiteralPath $normalizedPath -Raw | ConvertFrom-Json
$classCount = [int]$normalized.classCount
if ($classCount -lt $MinClassCount) {
    throw "Spellcasting class count ($classCount) is below MinClassCount ($MinClassCount). Aborting asset update."
}

Copy-Item -LiteralPath $normalizedPath -Destination (Join-Path $assetDir "class-spellcasting.normalized.json") -Force
if (Test-Path -LiteralPath $changelogPath) {
    Copy-Item -LiteralPath $changelogPath -Destination (Join-Path $assetDir "class-spellcasting.changelog.json") -Force
}

Write-Host "Class spellcasting dataset update complete."
Write-Host "  Class count: $classCount"
Write-Host "  Source commit: $SourceCommit"
Write-Host "  Asset dir: $assetDir"
