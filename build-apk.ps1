# SAFA — APK Build Script
# Run this ONCE after Android Studio is installed. It sets up everything automatically.
# Usage: .\build-apk.ps1

$ErrorActionPreference = "Stop"

Write-Host "`n=== SAFA APK Build Script ===" -ForegroundColor Cyan

# 1. Auto-detect Android SDK path from Android Studio
$sdkCandidates = @(
    "$env:LOCALAPPDATA\Android\Sdk",
    "$env:USERPROFILE\AppData\Local\Android\Sdk",
    "C:\Users\$env:USERNAME\AppData\Local\Android\Sdk"
)
$sdk = $sdkCandidates | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $sdk) {
    Write-Host "`nERROR: Android SDK not found." -ForegroundColor Red
    Write-Host "Please install Android Studio from: https://developer.android.com/studio" -ForegroundColor Yellow
    Write-Host "After install, re-run this script." -ForegroundColor Yellow
    exit 1
}

Write-Host "Found Android SDK: $sdk" -ForegroundColor Green

# 2. Write local.properties
$localProps = "sdk.dir=$($sdk -replace '\\', '\\')"
Set-Content -Path ".\local.properties" -Value $localProps -Encoding UTF8
Write-Host "Created local.properties" -ForegroundColor Green

# 3. Build debug APK
Write-Host "`nBuilding debug APK..." -ForegroundColor Cyan
Set-Location $PSScriptRoot
.\gradlew.bat assembleDebug --stacktrace
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build FAILED. Check errors above." -ForegroundColor Red
    exit 1
}

# 4. Find and show APK location
$apk = Get-ChildItem ".\app\build\outputs\apk\debug\*.apk" | Select-Object -First 1
if ($apk) {
    Write-Host "`nAPK built successfully!" -ForegroundColor Green
    Write-Host "APK location: $($apk.FullName)" -ForegroundColor Cyan
    Write-Host "APK size: $([math]::Round($apk.Length/1MB, 1)) MB" -ForegroundColor Cyan

    # 5. Install to connected device/emulator if adb is available
    $adb = "C:\platform-tools\adb.exe"
    if (Test-Path $adb) {
        $devices = & $adb devices 2>&1 | Select-String "device$"
        if ($devices) {
            Write-Host "`nInstalling APK on connected device..." -ForegroundColor Cyan
            & $adb install -r $apk.FullName
            Write-Host "APK installed!" -ForegroundColor Green
        } else {
            Write-Host "`nNo device connected. Start the emulator or plug in a device, then run:" -ForegroundColor Yellow
            Write-Host "  $adb install -r `"$($apk.FullName)`"" -ForegroundColor White
        }
    }
} else {
    Write-Host "APK not found — build may have failed." -ForegroundColor Red
}
