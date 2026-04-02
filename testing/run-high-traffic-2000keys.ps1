param(
  [string]$BaseUrl = "http://localhost:8082",
  [string]$Username = "admin",
  [string]$Password = "admin",
  [int]$ApiKeyCount = 2000,
  [int]$RateLimit = 50,
  [int]$WindowSeconds = 60,
  [int]$Tokens = 1,
  [int]$SleepMs = 0,
  [string]$Route = "/api/test/high-traffic",
  [string]$KeyFile = "",
  [switch]$CreateKeys,
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

if ($CreateKeys -or -not (Test-Path $KeyFile)) {
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
