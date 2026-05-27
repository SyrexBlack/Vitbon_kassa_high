param(
    [string]$BackendBaseUrl = "http://127.0.0.1:8080/",
    [string]$AdminPin = "9999",
    [string]$DeviceId = ("LIVE-EVIDENCE-" + [DateTime]::UtcNow.ToString("yyyyMMddHHmmss")),
    [string]$ChaseznakCode,
    [string]$AgeQrData,
    [string]$EgaisIncomingPayloadPath,
    [string]$EgaisTaraPayloadPath,
    [string]$SellCheckId = ("CHK-LIVE-" + [DateTime]::UtcNow.ToString("yyyyMMddHHmmss")),
    [switch]$EnableMutatingRoutes,
    [string]$ReportPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $repoRoot ".tmp_live_integrations_evidence.md"
}

function Write-Section {
    param([string]$Title)

    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

function Normalize-BaseUrl {
    param([string]$Value)

    $trimmed = $Value.Trim()
    if (-not $trimmed.EndsWith("/")) {
        return "$trimmed/"
    }

    return $trimmed
}

function Get-BodySnippet {
    param([AllowNull()][string]$Body)

    if ([string]::IsNullOrWhiteSpace($Body)) {
        return ""
    }

    $normalized = ($Body -replace "`r`n", " " -replace "`n", " ").Trim()
    if ($normalized.Length -le 300) {
        return $normalized
    }

    return $normalized.Substring(0, 300) + "..."
}

function New-StepResult {
    param(
        [string]$Name,
        [string]$Status,
        $HttpStatusCode,
        [string]$Detail,
        [string]$Body = ""
    )

    $statusLabel = if ($null -ne $HttpStatusCode) {
        "$Status (HTTP $HttpStatusCode)"
    } else {
        $Status
    }
    Write-Host "Result  : $statusLabel"
    if (-not [string]::IsNullOrWhiteSpace($Detail)) {
        $color = switch ($Status) {
            "PASS" { "Green" }
            "PENDING" { "Yellow" }
            default { "Red" }
        }
        Write-Host "Detail  : $Detail" -ForegroundColor $color
    }

    return [pscustomobject]@{
        Name = $Name
        Status = $Status
        HttpStatusCode = $HttpStatusCode
        Detail = $Detail
        Body = $Body
        BodySnippet = (Get-BodySnippet -Body $Body)
    }
}

function Invoke-HttpStep {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Uri,
        [int[]]$ExpectedStatusCodes,
        [hashtable]$Headers = @{},
        [AllowNull()][string]$Body,
        [string]$ContentType,
        [string]$PendingReason
    )

    Write-Section $Name

    if (-not [string]::IsNullOrWhiteSpace($PendingReason)) {
        return New-StepResult -Name $Name -Status "PENDING" -HttpStatusCode $null -Detail $PendingReason
    }

    Write-Host "Request : $Method $Uri"
    if (-not [string]::IsNullOrWhiteSpace($ContentType)) {
        Write-Host "Type    : $ContentType"
    }
    if (-not [string]::IsNullOrWhiteSpace($Body)) {
        Write-Host "Body    : $(Get-BodySnippet -Body $Body)"
    }

    try {
        $requestSplat = @{
            Uri = $Uri
            Method = $Method
            Headers = $Headers
            SkipHttpErrorCheck = $true
        }

        if (-not [string]::IsNullOrWhiteSpace($ContentType)) {
            $requestSplat.ContentType = $ContentType
        }
        if ($null -ne $Body) {
            $requestSplat.Body = $Body
        }

        $response = Invoke-WebRequest @requestSplat
        $statusCode = [int]$response.StatusCode
        $responseBody = [string]$response.Content

        if ($ExpectedStatusCodes -contains $statusCode) {
            return New-StepResult -Name $Name -Status "PASS" -HttpStatusCode $statusCode -Detail "Expected contour response" -Body $responseBody
        }

        return New-StepResult -Name $Name -Status "FAIL" -HttpStatusCode $statusCode -Detail "Unexpected HTTP status" -Body $responseBody
    } catch {
        return New-StepResult -Name $Name -Status "FAIL" -HttpStatusCode $null -Detail $_.Exception.Message
    }
}

function Read-OptionalPayload {
    param(
        [string]$Path,
        [string]$MissingMessage
    )

    if ([string]::IsNullOrWhiteSpace($Path)) {
        return [pscustomobject]@{
            PendingReason = $MissingMessage
            Body = $null
        }
    }

    $resolvedPath = Resolve-Path -Path $Path -ErrorAction Stop
    return [pscustomobject]@{
        PendingReason = ""
        Body = (Get-Content -Path $resolvedPath -Raw)
    }
}

function Add-ReportSection {
    param(
        [System.Collections.Generic.List[string]]$Lines,
        [string]$Title,
        [System.Collections.IEnumerable]$Steps
    )

    $Lines.Add("")
    $Lines.Add("## $Title")
    $Lines.Add("")
    $Lines.Add("| Step | Status | HTTP | Detail |")
    $Lines.Add("| --- | --- | --- | --- |")

    foreach ($step in $Steps) {
        $httpLabel = if ($null -ne $step.HttpStatusCode) { [string]$step.HttpStatusCode } else { "-" }
        $detail = ($step.Detail -replace "\|", "/")
        $Lines.Add("| $($step.Name) | $($step.Status) | $httpLabel | $detail |")
    }
}

$normalizedBaseUrl = Normalize-BaseUrl -Value $BackendBaseUrl
$steps = New-Object 'System.Collections.Generic.List[object]'

$loginBody = @{
    pin = $AdminPin
    deviceId = $DeviceId
} | ConvertTo-Json -Compress

$loginStep = Invoke-HttpStep `
    -Name "Auth login" `
    -Method "POST" `
    -Uri ($normalizedBaseUrl + "api/v1/auth/login") `
    -ExpectedStatusCodes @(200) `
    -Body $loginBody `
    -ContentType "application/json"
$steps.Add($loginStep)

$token = $null
$loginPayload = $null
if ($loginStep.Status -eq "PASS") {
    try {
        $loginPayload = $loginStep.Body | ConvertFrom-Json
        $token = [string]$loginPayload.token
    } catch {
        $steps.Add((New-StepResult -Name "Login response parse" -Status "FAIL" -HttpStatusCode $null -Detail $_.Exception.Message))
    }
}

if ($loginStep.Status -eq "PASS" -and $null -ne $loginPayload) {
    $egaisEnabled = [bool]$loginPayload.features.egaisEnabled
    $chaseznakEnabled = [bool]$loginPayload.features.chaseznakEnabled
    $featuresOk = $egaisEnabled -and $chaseznakEnabled
    $featureDetail = "egaisEnabled=$egaisEnabled; chaseznakEnabled=$chaseznakEnabled"
    $steps.Add((New-StepResult -Name "Feature flags for optional integrations" -Status $(if ($featuresOk) { "PASS" } else { "FAIL" }) -HttpStatusCode $null -Detail $featureDetail))
}

$headers = @{}
if (-not [string]::IsNullOrWhiteSpace($token)) {
    $headers["Authorization"] = "Bearer $token"
    $headers["X-Device-Id"] = $DeviceId
}

$steps.Add((Invoke-HttpStep `
    -Name "Statuses telemetry" `
    -Method "GET" `
    -Uri ($normalizedBaseUrl + "api/v1/statuses") `
    -ExpectedStatusCodes @(200) `
    -Headers $headers `
    -PendingReason $(if ([string]::IsNullOrWhiteSpace($token)) { "Skipped because login did not return a bearer token" } else { "" })))

$egaisStatusStep = Invoke-HttpStep `
    -Name "EGAIS route health" `
    -Method "GET" `
    -Uri ($normalizedBaseUrl + "api/v1/egais/status") `
    -ExpectedStatusCodes @(200) `
    -Headers $headers `
    -PendingReason $(if ([string]::IsNullOrWhiteSpace($token)) { "Skipped because login did not return a bearer token" } else { "" })

if ($egaisStatusStep.Status -eq "PASS") {
    try {
        $egaisStatusResponse = $egaisStatusStep.Body | ConvertFrom-Json
        if (-not [bool]$egaisStatusResponse.available) {
            $egaisStatusStep = New-StepResult -Name "EGAIS route health" -Status "FAIL" -HttpStatusCode 200 -Detail "available=false"
        } else {
            $egaisStatusStep = New-StepResult -Name "EGAIS route health" -Status "PASS" -HttpStatusCode 200 -Detail "available=true"
        }
    } catch {
        $egaisStatusStep = New-StepResult -Name "EGAIS route health" -Status "FAIL" -HttpStatusCode $null -Detail $_.Exception.Message
    }
}
$steps.Add($egaisStatusStep)

$czValidateBody = if ([string]::IsNullOrWhiteSpace($ChaseznakCode)) { $null } else { (@{ code = $ChaseznakCode } | ConvertTo-Json -Compress) }
$steps.Add((Invoke-HttpStep `
    -Name "Chestny ZNAK validate" `
    -Method "POST" `
    -Uri ($normalizedBaseUrl + "api/v1/chaseznak/validate") `
    -ExpectedStatusCodes @(200) `
    -Headers $headers `
    -Body $czValidateBody `
    -ContentType "application/json" `
    -PendingReason $(if ([string]::IsNullOrWhiteSpace($token)) { "Skipped because login did not return a bearer token" } elseif ([string]::IsNullOrWhiteSpace($ChaseznakCode)) { "Provide -ChaseznakCode to verify live contour validation" } else { "" })))

$ageVerifyBody = if ([string]::IsNullOrWhiteSpace($AgeQrData)) { $null } else { (@{ qrData = $AgeQrData } | ConvertTo-Json -Compress) }
$steps.Add((Invoke-HttpStep `
    -Name "Age verification contour" `
    -Method "POST" `
    -Uri ($normalizedBaseUrl + "api/v1/chaseznak/verify-age") `
    -ExpectedStatusCodes @(200) `
    -Headers $headers `
    -Body $ageVerifyBody `
    -ContentType "application/json" `
    -PendingReason $(if ([string]::IsNullOrWhiteSpace($token)) { "Skipped because login did not return a bearer token" } elseif ([string]::IsNullOrWhiteSpace($AgeQrData)) { "Provide -AgeQrData to verify live age-check contour" } else { "" })))

$incomingPayload = Read-OptionalPayload -Path $EgaisIncomingPayloadPath -MissingMessage "Provide -EgaisIncomingPayloadPath for live EGAIS incoming smoke"
$steps.Add((Invoke-HttpStep `
    -Name "EGAIS incoming contour" `
    -Method "POST" `
    -Uri ($normalizedBaseUrl + "api/v1/egais/incoming") `
    -ExpectedStatusCodes @(200, 400, 409, 422) `
    -Headers $headers `
    -Body $incomingPayload.Body `
    -ContentType "application/xml" `
    -PendingReason $(if ([string]::IsNullOrWhiteSpace($token)) { "Skipped because login did not return a bearer token" } elseif (-not $EnableMutatingRoutes) { "Skipped because -EnableMutatingRoutes was not set" } else { $incomingPayload.PendingReason })))

$taraPayload = Read-OptionalPayload -Path $EgaisTaraPayloadPath -MissingMessage "Provide -EgaisTaraPayloadPath for live EGAIS tara smoke"
$steps.Add((Invoke-HttpStep `
    -Name "EGAIS tara contour" `
    -Method "POST" `
    -Uri ($normalizedBaseUrl + "api/v1/egais/tara") `
    -ExpectedStatusCodes @(200, 400, 409, 422) `
    -Headers $headers `
    -Body $taraPayload.Body `
    -ContentType "application/xml" `
    -PendingReason $(if ([string]::IsNullOrWhiteSpace($token)) { "Skipped because login did not return a bearer token" } elseif (-not $EnableMutatingRoutes) { "Skipped because -EnableMutatingRoutes was not set" } else { $taraPayload.PendingReason })))

$sellBody = if ([string]::IsNullOrWhiteSpace($ChaseznakCode)) { $null } else { (@{ code = $ChaseznakCode; checkId = $SellCheckId } | ConvertTo-Json -Compress) }
$steps.Add((Invoke-HttpStep `
    -Name "Chestny ZNAK sell contour" `
    -Method "POST" `
    -Uri ($normalizedBaseUrl + "api/v1/chaseznak/sell") `
    -ExpectedStatusCodes @(200, 202, 400, 409, 422) `
    -Headers $headers `
    -Body $sellBody `
    -ContentType "application/json" `
    -PendingReason $(if ([string]::IsNullOrWhiteSpace($token)) { "Skipped because login did not return a bearer token" } elseif (-not $EnableMutatingRoutes) { "Skipped because -EnableMutatingRoutes was not set" } elseif ([string]::IsNullOrWhiteSpace($ChaseznakCode)) { "Provide -ChaseznakCode before attempting live sell/disposal" } else { "" })))

$reportLines = New-Object 'System.Collections.Generic.List[string]'
$reportLines.Add("# Live Integrations Evidence")
$reportLines.Add("")
$reportLines.Add("- Timestamp (UTC): $([DateTime]::UtcNow.ToString('yyyy-MM-dd HH:mm:ss'))")
$reportLines.Add("- BackendBaseUrl: $normalizedBaseUrl")
$reportLines.Add("- DeviceId: $DeviceId")
$reportLines.Add("- Mutating routes enabled: $EnableMutatingRoutes")
$reportLines.Add("- Report generated by: verify-live-integrations.ps1")

Add-ReportSection -Lines $reportLines -Title "Live contour summary" -Steps $steps

$reportLines.Add("")
$reportLines.Add("## Response snippets")
$reportLines.Add("")
foreach ($step in $steps) {
    if (-not [string]::IsNullOrWhiteSpace($step.BodySnippet)) {
        $reportLines.Add("### $($step.Name)")
        $reportLines.Add("")
        $reportLines.Add('```text')
        $reportLines.Add($step.BodySnippet)
        $reportLines.Add('```')
        $reportLines.Add("")
    }
}

$reportDirectory = Split-Path -Parent $ReportPath
if (-not [string]::IsNullOrWhiteSpace($reportDirectory)) {
    New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
}
Set-Content -Path $ReportPath -Value $reportLines -Encoding UTF8

Write-Section "Summary"
$steps | ForEach-Object {
    $httpLabel = if ($null -ne $_.HttpStatusCode) { $_.HttpStatusCode } else { "-" }
    Write-Host ("{0,-34} {1,-8} {2}" -f $_.Name, $_.Status, $httpLabel)
}
Write-Host "Report  : $ReportPath"

$failedSteps = @($steps | Where-Object { $_.Status -eq "FAIL" })
$pendingSteps = @($steps | Where-Object { $_.Status -eq "PENDING" })

if ($failedSteps.Count -gt 0) {
    Write-Host ""
    Write-Host "Live integrations evidence FAILED." -ForegroundColor Red
    exit 1
}

if ($pendingSteps.Count -gt 0) {
    Write-Host ""
    Write-Host "Live integrations evidence is still PENDING." -ForegroundColor Yellow
    Write-Host "Provide the missing payloads/test codes or enable mutating routes to complete the contour sweep." -ForegroundColor Yellow
    exit 2
}

Write-Host ""
Write-Host "Live integrations evidence completed." -ForegroundColor Green
exit 0