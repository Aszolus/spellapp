param(
    [Parameter(Mandatory = $true)]
    [string]$FoundryPf2ePacksDir,

    [Parameter(Mandatory = $true)]
    [string]$SourceCommit
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$importerScript = Join-Path $repoRoot "tools/importer/import_builder_catalog.py"
$importOutDir = Join-Path $repoRoot "tools/importer/out/builder"
$assetDir = Join-Path $repoRoot "app/src/main/assets"

if (-not (Test-Path -LiteralPath $importerScript)) {
    throw "Importer script not found: $importerScript"
}

if (-not (Test-Path -LiteralPath $assetDir)) {
    New-Item -ItemType Directory -Path $assetDir | Out-Null
}

python $importerScript `
    --packs-dir $FoundryPf2ePacksDir `
    --output-dir $importOutDir `
    --source-commit $SourceCommit

$manifestPath = Join-Path $importOutDir "builder.manifest.normalized.json"
if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Importer did not generate builder manifest: $manifestPath"
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
Copy-Item -LiteralPath $manifestPath -Destination (Join-Path $assetDir "builder.manifest.normalized.json") -Force
foreach ($asset in $manifest.assets) {
    $sourcePath = Join-Path $importOutDir $asset.name
    if (-not (Test-Path -LiteralPath $sourcePath)) {
        throw "Manifest references missing builder asset: $($asset.name)"
    }
    Copy-Item -LiteralPath $sourcePath -Destination (Join-Path $assetDir $asset.name) -Force
}

Write-Host "Builder catalog update complete."
Write-Host "  Classes: $($manifest.counts.classes)"
Write-Host "  Ancestries: $($manifest.counts.ancestries)"
Write-Host "  Heritages: $($manifest.counts.heritages)"
Write-Host "  Backgrounds: $($manifest.counts.backgrounds)"
Write-Host "  Feats: $($manifest.counts.feats)"
Write-Host "  Feat shards: $($manifest.counts.featShards)"
Write-Host "  Source commit: $SourceCommit"
Write-Host "  Asset dir: $assetDir"
