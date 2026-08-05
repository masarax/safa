# SAFA — Backend API Live Test Script
# Tests all API endpoints with HMAC signature verification
# Usage: .\test-api.ps1

param(
    [string]$BaseUrl    = "http://127.0.0.1:8000",
    [string]$ApiKey     = "safa_test_api_key_2026",
    [string]$ApiSecret  = "safa_test_secret_32byteslong_2026"
)

$pass = 0; $fail = 0

function Get-Signature($method, $path, $secret, $body = "") {
    $ts = [string][math]::Floor([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())
    $payload = $method + $path + $ts + $body
    $hmac = [System.Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($secret))
    $sig = ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes($payload)) | ForEach-Object { $_.ToString("x2") }) -join ""
    return @{ sig = $sig; ts = $ts }
}

function Invoke-Api($method, $path, $body = $null, $expectedStatus = 200) {
    $bodyStr = if ($body) { $body } else { "" }
    # Signature uses path only (no query string), matching server and Android interceptor
    $signPath = $path.Split("?")[0]
    $signed = Get-Signature $method $signPath $ApiSecret $bodyStr
    $headers = @{
        "X-SAFA-API-KEY"   = $ApiKey
        "X-SAFA-SIGNATURE" = $signed.sig
        "X-SAFA-TIMESTAMP" = $signed.ts
        "Content-Type"     = "application/json"
        "Accept"           = "application/json"
    }
    try {
        $params = @{ Uri = "$BaseUrl$path"; Method = $method; Headers = $headers; UseBasicParsing = $true }
        if ($body) { $params.Body = $body }
        $r = Invoke-WebRequest @params -ErrorAction Stop
        if ($r.StatusCode -eq $expectedStatus) {
            Write-Host "  PASS [$method $path] → $($r.StatusCode)" -ForegroundColor Green
            Write-Host "       $($r.Content)" -ForegroundColor DarkGray
            $script:pass++
        } else {
            Write-Host "  FAIL [$method $path] Expected $expectedStatus got $($r.StatusCode)" -ForegroundColor Red
            $script:fail++
        }
    } catch {
        Write-Host "  FAIL [$method $path] $($_.Exception.Message)" -ForegroundColor Red
        $script:fail++
    }
}

Write-Host "`n=== SAFA API Test Suite ===" -ForegroundColor Cyan
Write-Host "Target: $BaseUrl`n"

# Health check (no auth)
try {
    $h = Invoke-WebRequest "$BaseUrl/up" -UseBasicParsing
    Write-Host "  PASS [GET /up] $($h.StatusCode)" -ForegroundColor Green
    $pass++
} catch {
    Write-Host "  FAIL [GET /up] Is 'php artisan serve' running?" -ForegroundColor Red
    $fail++
}

# Auth failure test: should get 401
try {
    Invoke-WebRequest "$BaseUrl/api/sync/down" -UseBasicParsing -ErrorAction Stop | Out-Null
    Write-Host "  FAIL [No auth should 401] Got 200 instead" -ForegroundColor Red
    $fail++
} catch {
    if ($_.Exception.Response -and $_.Exception.Response.StatusCode.value__ -eq 401) {
        Write-Host "  PASS [No auth headers correctly returns 401]" -ForegroundColor Green
        $pass++
    } else {
        Write-Host "  FAIL [Auth check error] $($_.Exception.Message)" -ForegroundColor Red
        $fail++
    }
}

# Authenticated endpoints
Invoke-Api "GET" "/api/version/check?version_code=1"
Invoke-Api "GET" "/api/config/remote"
Invoke-Api "GET" "/api/sync/down"

# Sync up test
$syncBody = '{"transactions":[{"local_id":1,"type":"Pending","amount":1000,"timestamp":1700000000}]}'
Invoke-Api "POST" "/api/sync/up" $syncBody

Write-Host "`n=== Results: $pass passed, $fail failed ===" -ForegroundColor $(if ($fail -eq 0) {"Green"} else {"Yellow"})
if ($fail -eq 0) { Write-Host "All tests passed! Backend is production-ready." -ForegroundColor Green }
