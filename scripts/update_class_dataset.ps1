param(
    [Parameter(Mandatory = $true)]
    [string]$FoundryClassesDir,

    [Parameter(Mandatory = $true)]
    [string]$SourceCommit,

    [int]$MinClassCount = 15
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$importerScript = Join-Path $repoRoot "tools/importer/import_classes.ps1"
$importOutDir = Join-Path $repoRoot "tools/importer/out"
$assetDir = Join-Path $repoRoot "app/src/main/assets"

if (-not (Test-Path -LiteralPath $importerScript)) {
    throw "Importer script not found: $importerScript"
}

if (-not (Test-Path -LiteralPath $assetDir)) {
    New-Item -ItemType Directory -Path $assetDir | Out-Null
}

& $importerScript `
    -InputDir $FoundryClassesDir `
    -OutputDir $importOutDir `
    -SourceCommit $SourceCommit

$normalizedPath = Join-Path $importOutDir "classes.normalized.json"
$attributionPath = Join-Path $importOutDir "classes.attribution.json"
$changelogPath = Join-Path $importOutDir "classes.changelog.json"

if (-not (Test-Path -LiteralPath $normalizedPath)) {
    throw "Importer did not generate normalized dataset: $normalizedPath"
}

$normalized = Get-Content -LiteralPath $normalizedPath -Raw | ConvertFrom-Json
$classCount = [int]$normalized.classCount
if ($classCount -lt $MinClassCount) {
    throw "Class count ($classCount) is below MinClassCount ($MinClassCount). Aborting asset update."
}

Copy-Item -LiteralPath $normalizedPath -Destination (Join-Path $assetDir "classes.normalized.json") -Force
if (Test-Path -LiteralPath $attributionPath) {
    Copy-Item -LiteralPath $attributionPath -Destination (Join-Path $assetDir "classes.attribution.json") -Force
}
if (Test-Path -LiteralPath $changelogPath) {
    Copy-Item -LiteralPath $changelogPath -Destination (Join-Path $assetDir "classes.changelog.json") -Force
}

Write-Host "Class dataset update complete."
Write-Host "  Class count: $classCount"
Write-Host "  Source commit: $SourceCommit"
Write-Host "  Asset dir: $assetDir"
