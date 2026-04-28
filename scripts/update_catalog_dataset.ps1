param(
    [Parameter(Mandatory = $true)]
    [string]$FoundryPf2eRoot,

    [Parameter(Mandatory = $true)]
    [string]$SourceCommit,

    [Parameter(Mandatory = $false)]
    [long]$WarnSizeBytes = 41943040,

    [Parameter(Mandatory = $false)]
    [long]$MaxSizeBytes = 83886080,

    [Parameter(Mandatory = $false)]
    [switch]$AllowOversize,

    [Parameter(Mandatory = $false)]
    [switch]$StrictReferences
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$importerScript = Join-Path $repoRoot "tools/importer/import_catalog.py"
$outputDir = Join-Path $repoRoot "tools/importer/out/catalog"

if (-not (Test-Path -LiteralPath $importerScript)) {
    throw "Importer script not found: $importerScript"
}

if (-not (Test-Path -LiteralPath $FoundryPf2eRoot)) {
    throw "Foundry PF2e root not found: $FoundryPf2eRoot"
}

$arguments = @(
    $importerScript,
    "--foundry-pf2e-root", $FoundryPf2eRoot,
    "--output-dir", $outputDir,
    "--source-commit", $SourceCommit,
    "--warn-size-bytes", "$WarnSizeBytes",
    "--max-size-bytes", "$MaxSizeBytes"
)

if ($AllowOversize) {
    $arguments += "--allow-oversize"
}

if ($StrictReferences) {
    $arguments += "--strict-references"
}

python @arguments
if ($LASTEXITCODE -ne 0) {
    throw "Catalog importer failed with exit code $LASTEXITCODE"
}

$manifestPath = Join-Path $outputDir "catalog.manifest.json"
$auditPath = Join-Path $outputDir "catalog.audit.json"
$dbPath = Join-Path $outputDir "catalog.db"

if (-not (Test-Path -LiteralPath $manifestPath)) {
    throw "Importer did not generate catalog manifest: $manifestPath"
}
if (-not (Test-Path -LiteralPath $auditPath)) {
    throw "Importer did not generate catalog audit: $auditPath"
}
if (-not (Test-Path -LiteralPath $dbPath)) {
    throw "Importer did not generate catalog database: $dbPath"
}

$manifest = Get-Content -LiteralPath $manifestPath -Raw -Encoding UTF8 | ConvertFrom-Json
$audit = Get-Content -LiteralPath $auditPath -Raw -Encoding UTF8 | ConvertFrom-Json

Write-Host "Catalog dataset update complete."
Write-Host "  Catalog schema: $($manifest.catalog_schema_version)"
Write-Host "  PF2e system version: $($manifest.pf2e_system_version)"
Write-Host "  Source commit: $($manifest.source_commit)"
Write-Host "  Records: $($manifest.counts.records)"
Write-Host "  Links: $($manifest.counts.links.total) total / $($manifest.counts.links.resolved) resolved"
Write-Host "  Warnings: $($audit.counts.issues.warnings)"
Write-Host "  DB size: $($manifest.database.sizeBytes) bytes"
Write-Host "  Output dir: $outputDir"
