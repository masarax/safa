# SAFA — APK Build & Setup Script for Local Mobile Testing

param (
    [switch]$Release
)

$ErrorActionPreference = "Stop"

$buildType = if ($Release) { "Release" } else { "Debug" }
$gradleTask = if ($Release) { "assembleRelease" } else { "assembleDebug" }

Write-Host "`n=== SAFA Android $buildType APK Builder ===" -ForegroundColor Cyan

# 1. Environment variables setup
if (-not $env:JAVA_HOME) {
    $env:JAVA_HOME = "C:\Android\jdk17"
    $env:Path = "$env:JAVA_HOME\bin;" + $env:Path
}
Write-Host "Java JDK Home: $env:JAVA_HOME" -ForegroundColor Green

# 2. Setup local.properties
$androidHome = if ($env:ANDROID_HOME) { $env:ANDROID_HOME -replace '\\', '/' } else { "C:/Android/sdk" }
$localProps = "sdk.dir=$androidHome"
Set-Content -Path ".\local.properties" -Value $localProps -Encoding UTF8
Write-Host "Created local.properties pointing to sdk.dir=$androidHome" -ForegroundColor Green

# 3. Build APK
Write-Host "`nBuilding $buildType APK ($gradleTask)..." -ForegroundColor Cyan
Set-Location $PSScriptRoot
.\gradlew.bat $gradleTask --stacktrace

if ($LASTEXITCODE -eq 0) {
    $apkDir = if ($Release) { "release" } else { "debug" }
    $apk = Get-ChildItem ".\app\build\outputs\apk\$apkDir\*.apk" | Select-Object -First 1
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
