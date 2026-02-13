Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$manifestPath = "app/src/main/AndroidManifest.xml"
if (-not (Test-Path $manifestPath)) {
    throw "Manifest file not found: $manifestPath"
}

$manifestText = Get-Content -Path $manifestPath -Raw
if ($manifestText -match "android.permission.INTERNET") {
    throw "INTERNET permission detected in $manifestPath"
}

$bannedPatterns = @(
    "com.squareup.okhttp3",
    "com.squareup.retrofit2",
    "io.ktor",
    "com.android.volley"
)

$gradleFiles = Get-ChildItem -Recurse -Filter *.gradle.kts | Select-Object -ExpandProperty FullName
$violations = @()
foreach ($file in $gradleFiles) {
    $lines = Get-Content -Path $file
    foreach ($line in $lines) {
        if ($line -notmatch "^\s*(implementation|api|debugImplementation|releaseImplementation)\s*\(") {
            continue
        }

        foreach ($pattern in $bannedPatterns) {
            if ($line -match [regex]::Escape($pattern)) {
                $violations += "$file -> $pattern"
            }
        }
    }
}

if ($violations.Count -gt 0) {
    $joined = $violations -join "`n"
    throw "Banned networking dependency pattern found:`n$joined"
}

Write-Host "Network isolation checks passed."
