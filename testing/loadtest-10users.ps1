param(
  [string]$BaseUrl = "http://localhost:8082",
  [string]$Username = "admin",
  [string]$Password = "admin",
  [string[]]$ApiKeys,
  [int]$RequestsPerUser = 50
)

if (-not $ApiKeys -or $ApiKeys.Count -eq 0) {
  throw "Provide -ApiKeys with real API keys (up to 10 recommended)."
}

$jobs = @()

1..10 | ForEach-Object {
  $userIndex = $_
  $apiKey = $ApiKeys[($userIndex - 1) % $ApiKeys.Count]

  $jobs += Start-Job -ScriptBlock {
    param($BaseUrl, $Username, $Password, $ApiKey, $RequestsPerUser)

    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $loginBody = @{ username = $Username; password = $Password } | ConvertTo-Json

    try {
      Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/auth/login" -WebSession $session -ContentType "application/json" -Body $loginBody | Out-Null
    } catch {
      return [pscustomobject]@{ User = $ApiKey; Allowed = 0; Blocked = 0; Other = $RequestsPerUser; Error = "Login failed" }
    }

    $allowed = 0
    $blocked = 0
    $other = 0
    $body = @{ apiKey = $ApiKey; route = "/api/test" } | ConvertTo-Json

    1..$RequestsPerUser | ForEach-Object {
      try {
        Invoke-RestMethod -Method POST -Uri "$BaseUrl/api/limit/check" -WebSession $session -ContentType "application/json" -Body $body | Out-Null
        $allowed++
      } catch {
        $status = $null
        if ($_.Exception.Response) {
          $status = $_.Exception.Response.StatusCode.value__
        }
        if ($status -eq 429) { $blocked++ } else { $other++ }
      }
    }

    [pscustomobject]@{ User = $ApiKey; Allowed = $allowed; Blocked = $blocked; Other = $other; Error = "" }
  } -ArgumentList $BaseUrl, $Username, $Password, $apiKey, $RequestsPerUser
}

$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job -Force | Out-Null

$results | Format-Table -AutoSize

$totalAllowed = ($results | Measure-Object -Property Allowed -Sum).Sum
$totalBlocked = ($results | Measure-Object -Property Blocked -Sum).Sum
$totalOther = ($results | Measure-Object -Property Other -Sum).Sum

Write-Host "TOTAL -> Allowed=$totalAllowed Blocked=$totalBlocked Other=$totalOther"
