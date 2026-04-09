param(
  [string]$BaseUrl = "http://localhost:8082",
  [string]$Username = "admin",
  [string]$Password = "admin@2026",
  [int]$ApiKeyCount = 2000,
  [int]$RateLimit = 50,
  [int]$WindowSeconds = 60,
  [int]$Tokens = 1,
  [int]$SleepMs = 0,
  [string]$Route = "/api/test/high-traffic",
  [string]$KeyFile = "",
  [switch]$CreateKeys,
  [switch]$UseDbKeys,
  [switch]$UseRandomKeys
)

$ErrorActionPreference = "Stop"

if (-not $KeyFile) {
  $KeyFile = Join-Path $PSScriptRoot "generated-api-keys.txt"
}

function Require-Command([string]$Name) {
  if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
    throw "Required command '$Name' was not found in PATH."
  }
}

Require-Command "k6"

function Get-AuthHeaders([string]$BaseUrl, [string]$Username, [string]$Password) {
  $loginBody = @{
    username = $Username
    password = $Password
  } | ConvertTo-Json

  try {
    $response = Invoke-RestMethod `
      -Method POST `
      -Uri "$BaseUrl/api/auth/login" `
      -ContentType "application/json" `
      -Body $loginBody
  } catch {
    throw "Login failed. Check backend status and admin credentials."
  }

  if (-not $response.token) {
    throw "Login failed. Token missing from response."
  }

  $tokenType = if ($response.tokenType) { $response.tokenType } else { "Bearer" }
  return @{
    Authorization = "$tokenType $($response.token)"
    "Content-Type" = "application/json"
  }
}

function Export-ApiKeysFromDb([string]$BaseUrl, [string]$Username, [string]$Password, [string]$KeyFile, [int]$Limit) {
  Write-Host "Fetching API keys from backend..." -ForegroundColor Cyan
  $headers = Get-AuthHeaders -BaseUrl $BaseUrl -Username $Username -Password $Password
  $keys = Invoke-RestMethod `
    -Method GET `
    -Uri "$BaseUrl/api" `
    -Headers $headers

  if (-not $keys) {
    throw "No API keys returned from backend."
  }

  $apiKeys = $keys | Where-Object { $_.apiKey } | Select-Object -ExpandProperty apiKey
  if ($Limit -gt 0 -and $apiKeys.Count -gt $Limit) {
    $apiKeys = $apiKeys | Select-Object -First $Limit
  }

  if (-not $apiKeys -or $apiKeys.Count -eq 0) {
    throw "API key list is empty after filtering."
  }

  $apiKeys | Set-Content -Path $KeyFile
  Write-Host "Saved $($apiKeys.Count) keys to $KeyFile" -ForegroundColor Green
}

if ($UseDbKeys) {
  Export-ApiKeysFromDb -BaseUrl $BaseUrl -Username $Username -Password $Password -KeyFile $KeyFile -Limit $ApiKeyCount
} elseif ($CreateKeys -or -not (Test-Path $KeyFile)) {
  Write-Host "Creating $ApiKeyCount API keys..." -ForegroundColor Cyan
  & powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot "bulk-create-api-keys.ps1") `
    -BaseUrl $BaseUrl `
    -Username $Username `
    -Password $Password `
    -Count $ApiKeyCount `
    -RateLimit $RateLimit `
    -WindowSeconds $WindowSeconds

  if (-not (Test-Path $KeyFile)) {
    throw "API key file was not created at $KeyFile."
  }
}

$strategy = if ($UseRandomKeys) { "random" } else { "per-vu" }

Write-Host "Running high-traffic k6 test with $ApiKeyCount API keys..." -ForegroundColor Cyan
Write-Host "Key file: $KeyFile"
Write-Host "Strategy: $strategy"

$env:BASE_URL = $BaseUrl
$env:AUTH_USER = $Username
$env:AUTH_PASS = $Password
$env:API_KEYS_FILE = $KeyFile
$env:TOKENS = "$Tokens"
$env:SLEEP_MS = "$SleepMs"
$env:TEST_ROUTE = $Route
$env:API_KEY_STRATEGY = $strategy
$env:START_VUS = "0"
$env:STAGE_1_TARGET = "250"
$env:STAGE_2_TARGET = "750"
$env:STAGE_3_TARGET = "1500"
$env:STAGE_4_TARGET = "2500"
$env:STAGE_1_DURATION = "45s"
$env:STAGE_2_DURATION = "90s"
$env:STAGE_3_DURATION = "120s"
$env:STAGE_4_DURATION = "120s"
$env:STAGE_5_DURATION = "45s"

& k6 run (Join-Path $PSScriptRoot "loadtest-k6.js")
