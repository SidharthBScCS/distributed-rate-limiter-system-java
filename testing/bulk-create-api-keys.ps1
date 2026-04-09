param(
  [string]$BaseUrl = "http://localhost:8082",
  [string]$Username = "admin",
  [string]$Password = "admin@2026",
  [int]$Count = 1000,
  [string]$UserPrefix = "loadtest-user",
  [int]$RateLimit = 10,
  [int]$WindowSeconds = 60
)

if ($Count -lt 1) {
  throw "Count must be at least 1."
}

$loginBody = @{
  username = $Username
  password = $Password
} | ConvertTo-Json

function Get-AuthHeaders([string]$BaseUrl, [string]$LoginBody) {
  try {
    $response = Invoke-RestMethod `
      -Method POST `
      -Uri "$BaseUrl/api/auth/login" `
      -ContentType "application/json" `
      -Body $LoginBody
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

$authHeaders = Get-AuthHeaders -BaseUrl $BaseUrl -LoginBody $loginBody

$created = New-Object System.Collections.Generic.List[string]

for ($i = 1; $i -le $Count; $i++) {
  $payload = @{
    userName = "$UserPrefix-$i"
    rateLimit = "$RateLimit"
    windowSeconds = "$WindowSeconds"
  } | ConvertTo-Json

  try {
    $response = Invoke-RestMethod `
      -Method POST `
      -Uri "$BaseUrl/api/keys" `
      -Headers $authHeaders `
      -Body $payload

    $created.Add($response.apiKey) | Out-Null
  } catch {
    $status = $null
    if ($_.Exception.Response) {
      $status = $_.Exception.Response.StatusCode.value__
    }
    Write-Error "Failed at item $i (HTTP $status). Stopping."
    break
  }

  if ($i % 100 -eq 0 -or $i -eq $Count) {
    Write-Host "Created $i / $Count API keys"
  }
}

$outputPath = Join-Path $PSScriptRoot "generated-api-keys.txt"
$created | Set-Content -Path $outputPath

Write-Host ""
Write-Host "Finished."
Write-Host "Generated keys: $($created.Count)"
Write-Host "Saved to: $outputPath"
