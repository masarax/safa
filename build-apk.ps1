# SAFA — APK Build & Setup Script for Local Mobile Testing

$ErrorActionPreference = "Stop"

Write-Host "`n=== SAFA Android Debug APK Builder ===" -ForegroundColor Cyan

# 1. Environment variables setup
$env:JAVA_HOME = "C:\Android\jdk17"
$env:Path = "C:\Android\jdk17\bin;" + $env:Path

Write-Host "Java JDK 17 Path: $env:JAVA_HOME" -ForegroundColor Green

# 2. Setup local.properties
$localProps = "sdk.dir=C\:\\Android\\sdk"
Set-Content -Path ".\local.properties" -Value $localProps -Encoding UTF8
Write-Host "Created local.properties pointing to C:\Android\sdk" -ForegroundColor Green

# 3. Build debug APK
Write-Host "`nBuilding Debug APK (assembleDebug)..." -ForegroundColor Cyan
Set-Location $PSScriptRoot
.\gradlew.bat assembleDebug --stacktrace

if ($LASTEXITCODE -eq 0) {
    $apk = Get-ChildItem ".\app\build\outputs\apk\debug\*.apk" | Select-Object -First 1
    if ($apk) {
        Write-Host "`n==========================================" -ForegroundColor Green
        Write-Host " SUCCESS! APK Built Successfully!" -ForegroundColor Green
        Write-Host "==========================================" -ForegroundColor Green
        Write-Host "APK Location : $($apk.FullName)" -ForegroundColor Yellow
        Write-Host "APK Size     : $([math]::Round($apk.Length/1MB, 2)) MB" -ForegroundColor Yellow
    }
} else {
    Write-Host "`nBuild FAILED. Check log errors above." -ForegroundColor Red
}
