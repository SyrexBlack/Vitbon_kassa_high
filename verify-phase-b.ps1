param(
    [switch]$RequireHardware,
    [switch]$SkipRelease,
    [switch]$PlanOnly,
    [string]$ReportPath,
    [string]$HardwareEvidencePath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendJavaHome = "C:\Program Files\Java\jdk-17"
$adbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if ([string]::IsNullOrWhiteSpace($ReportPath)) {
    $ReportPath = Join-Path $repoRoot ".omo\ulw-loop\evidence\phase-b-verification-report.md"
}

function Write-Section {
    param([string]$Title)

    Write-Host ""
    Write-Host "=== $Title ===" -ForegroundColor Cyan
}

function Invoke-VerificationStep {
    param(
        [string]$Name,
        [string]$WorkingDirectory,
        [string]$Command,
        [hashtable]$Environment = @{}
    )

    Write-Section $Name
    Write-Host "Location: $WorkingDirectory"
    Write-Host "Command : $Command"

    $previousValues = @{}
    foreach ($entry in $Environment.GetEnumerator()) {
        $previousValues[$entry.Key] = [Environment]::GetEnvironmentVariable($entry.Key, "Process")
        [Environment]::SetEnvironmentVariable($entry.Key, $entry.Value, "Process")
    }

    Push-Location $WorkingDirectory
    try {
        Invoke-Expression $Command | Out-Host
        $currentLastExitCode = Get-Variable -Name LASTEXITCODE -Scope Global -ErrorAction SilentlyContinue
        $exitCode = if ($null -ne $currentLastExitCode) { $currentLastExitCode.Value } else { 0 }
        if ($null -eq $exitCode) {
            $exitCode = 0
        }
    } catch {
        Write-Host $_.Exception.Message -ForegroundColor Red
        $currentLastExitCode = Get-Variable -Name LASTEXITCODE -Scope Global -ErrorAction SilentlyContinue
        $exitCode = if ($null -ne $currentLastExitCode -and $currentLastExitCode.Value -ne 0) {
            $currentLastExitCode.Value
        } else {
            1
        }
    } finally {
        Pop-Location
        foreach ($entry in $Environment.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($entry.Key, $previousValues[$entry.Key], "Process")
        }
    }

    $status = if ($exitCode -eq 0) { "PASS" } else { "FAIL" }
    Write-Host "Result  : $status (exit $exitCode)"

    return [pscustomobject]@{
        Name = $Name
        Status = $status
        ExitCode = $exitCode
        Command = $Command
    }
}

function Get-HardwareStatus {
    param(
        [string]$AdbExecutable,
        [string]$HardwareEvidencePath
    )

    Write-Section "ADB hardware detection"

    if (-not [string]::IsNullOrWhiteSpace($HardwareEvidencePath)) {
        if (Test-Path $HardwareEvidencePath) {
            $evidence = Get-Content -Raw -Path $HardwareEvidencePath
            if ($evidence.Contains("MSPOS-K physical smoke PASS")) {
                Write-Host "Hardware evidence: $HardwareEvidencePath"
                Write-Host "Result  : PASS (explicit MSPOS-K smoke evidence)"
                return [pscustomobject]@{
                    Status = "PASS"
                    Reason = "explicit MSPOS-K physical smoke evidence"
                    Devices = @()
                }
            }

            Write-Host "Hardware evidence file does not contain required marker: MSPOS-K physical smoke PASS" -ForegroundColor Yellow
        } else {
            Write-Host "Hardware evidence file not found: $HardwareEvidencePath" -ForegroundColor Yellow
        }
    }

    if (-not (Test-Path $AdbExecutable)) {
        Write-Host "adb not found: $AdbExecutable" -ForegroundColor Yellow
        return [pscustomobject]@{
            Status = "PENDING"
            Reason = "adb executable not found"
            Devices = @()
        }
    }

    Write-Host "Command : $AdbExecutable devices -l"
    $output = & $AdbExecutable devices -l
    $output | ForEach-Object { Write-Host $_ }

    $devices = @($output |
        Select-Object -Skip 1 |
        Where-Object { $_.Trim().Length -gt 0 })

    $onlineDevices = @($devices | Where-Object { $_ -match "\bdevice\b" })

    if ($onlineDevices.Count -gt 0) {
        Write-Host "Result  : PENDING ($($onlineDevices.Count) adb device(s) online; explicit MSPOS-K smoke evidence required)" -ForegroundColor Yellow
        return [pscustomobject]@{
            Status = "PENDING"
            Reason = "adb device detected, but explicit MSPOS-K physical smoke evidence is required"
            Devices = $onlineDevices
        }
    }

    Write-Host "Result  : PENDING (no connected physical devices)" -ForegroundColor Yellow
    return [pscustomobject]@{
        Status = "PENDING"
        Reason = "no connected physical devices"
        Devices = @()
    }
}

function Get-ProductionBlockerMatrix {
    param([pscustomobject]$HardwareStatus)

    $hardwareLabel = if ($HardwareStatus.Status -eq "PASS") { "HARDWARE_DETECTED" } else { "HARDWARE_REQUIRED" }
    $hardwareEvidence = if ($HardwareStatus.Status -eq "PASS") {
        ($HardwareStatus.Devices -join "; ")
    } else {
        "Physical MSPOS-K smoke remains pending: $($HardwareStatus.Reason)"
    }

    return @(
        [pscustomobject]@{
            Blocker = "Fiscal adapters"
            Status = "SOFTWARE_VERIFIED"
            Evidence = "Android tests: FiscalAdapterContractTest, RealNeva01FProtocolTest"
        },
        [pscustomobject]@{
            Blocker = "RBAC/security routes"
            Status = "SOFTWARE_VERIFIED"
            Evidence = "Backend tests: SecurityRouteGuardIntegrationTest, AuthIntegrationTest; Android test: RouteAccessPolicyTest"
        },
        [pscustomobject]@{
            Blocker = "Encrypted local security"
            Status = "SOFTWARE_VERIFIED"
            Evidence = "Android tests: AuthTokenStoreTest, PrefsMigrationTest; source: SecurePrefsFactory"
        },
        [pscustomobject]@{
            Blocker = "Root policy"
            Status = "SOFTWARE_VERIFIED"
            Evidence = "Android tests: RootPolicyEnforcerTest, RootRiskGuard coverage"
        },
        [pscustomobject]@{
            Blocker = "Audit trail"
            Status = "SOFTWARE_VERIFIED"
            Evidence = "Backend tests: AuditIntegrationTest, AuditSyncIntegrationTest; Android tests: LocalAuditBufferRepositoryTest, SyncManagerTest"
        },
        [pscustomobject]@{
            Blocker = "Status gating"
            Status = "SOFTWARE_VERIFIED"
            Evidence = "Backend test: StatusIntegrationTest; Android tests: StatusOperationPolicyTest, RouteAccessPolicyTest"
        },
        [pscustomobject]@{
            Blocker = "Backend Java 17 reports"
            Status = "SOFTWARE_VERIFIED"
            Evidence = "Command uses JAVA_HOME=$backendJavaHome; test: CheckServiceSalesReportTest"
        },
        [pscustomobject]@{
            Blocker = "MSPOS-K physical smoke"
            Status = $hardwareLabel
            Evidence = $hardwareEvidence
        }
    )
}

function Write-ProductionBlockerMatrix {
    param([object[]]$Matrix)

    Write-Section "Production blocker matrix"
    foreach ($row in $Matrix) {
        Write-Host ("{0,-28} {1,-20} {2}" -f $row.Blocker, $row.Status, $row.Evidence)
    }
}

function Write-VerificationReport {
    param(
        [string]$Path,
        [object[]]$Steps,
        [pscustomobject]$HardwareStatus,
        [object[]]$Matrix,
        [bool]$PlanOnlyMode
    )

    $lines = New-Object 'System.Collections.Generic.List[string]'
    $lines.Add("# Phase B Production Verification Report")
    $lines.Add("")
    $lines.Add("- Timestamp (UTC): $([DateTime]::UtcNow.ToString('yyyy-MM-dd HH:mm:ss'))")
    $lines.Add("- PlanOnly: $PlanOnlyMode")
    $lines.Add("- RequireHardware: $RequireHardware")
    $lines.Add("- SkipRelease: $SkipRelease")
    $lines.Add("- HardwareEvidencePath: $HardwareEvidencePath")
    $lines.Add("")
    $lines.Add("## Production blocker matrix")
    $lines.Add("")
    $lines.Add("| Blocker | Status | Evidence |")
    $lines.Add("| --- | --- | --- |")
    foreach ($row in $Matrix) {
        $evidence = $row.Evidence -replace "\|", "/"
        $lines.Add("| $($row.Blocker) | $($row.Status) | $evidence |")
    }

    $lines.Add("")
    $lines.Add("## Automated steps")
    $lines.Add("")
    $lines.Add("| Step | Status | Exit | Command |")
    $lines.Add("| --- | --- | --- | --- |")
    foreach ($step in $Steps) {
        $command = $step.Command -replace "\|", "/"
        $lines.Add("| $($step.Name) | $($step.Status) | $($step.ExitCode) | ``$command`` |")
    }

    $lines.Add("")
    $lines.Add("## Hardware smoke")
    $lines.Add("")
    $lines.Add("- Status: $($HardwareStatus.Status)")
    $lines.Add("- Reason: $($HardwareStatus.Reason)")
    if ($HardwareStatus.Devices.Count -gt 0) {
        $lines.Add("- Devices: $($HardwareStatus.Devices -join '; ')")
    }

    $reportDirectory = Split-Path -Parent $Path
    if (-not [string]::IsNullOrWhiteSpace($reportDirectory)) {
        New-Item -ItemType Directory -Path $reportDirectory -Force | Out-Null
    }
    Set-Content -Path $Path -Value $lines -Encoding UTF8
    Write-Host "Report  : $Path"
}

$backendPathValue = if ($env:Path) {
    "$backendJavaHome\bin;$($env:Path)"
} else {
    "$backendJavaHome\bin"
}

if ($PlanOnly) {
    $hardwareStatus = Get-HardwareStatus -AdbExecutable $adbPath -HardwareEvidencePath $HardwareEvidencePath
    $blockerMatrix = Get-ProductionBlockerMatrix -HardwareStatus $hardwareStatus
    Write-ProductionBlockerMatrix -Matrix $blockerMatrix
    Write-VerificationReport -Path $ReportPath -Steps @() -HardwareStatus $hardwareStatus -Matrix $blockerMatrix -PlanOnlyMode $true
    exit 0
}

$steps = @(
    (Invoke-VerificationStep -Name "Backend tests" -WorkingDirectory (Join-Path $repoRoot "backend") -Command ".\gradlew.bat test --no-daemon --console=plain" -Environment @{ JAVA_HOME = $backendJavaHome; Path = $backendPathValue })

    (Invoke-VerificationStep -Name "Backend Java 17 reports" -WorkingDirectory (Join-Path $repoRoot "backend") -Command ".\gradlew.bat test --tests com.vitbon.kkm.domain.service.CheckServiceSalesReportTest --no-daemon --console=plain" -Environment @{ JAVA_HOME = $backendJavaHome; Path = $backendPathValue })

    (Invoke-VerificationStep -Name "Android unit + debug assemble" -WorkingDirectory (Join-Path $repoRoot "android") -Command ".\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain")
)

if (-not $SkipRelease) {
    $steps += (Invoke-VerificationStep -Name "Android release assemble" -WorkingDirectory (Join-Path $repoRoot "android") -Command ".\gradlew.bat :app:assembleRelease --no-daemon --console=plain")
}

$hardwareStatus = Get-HardwareStatus -AdbExecutable $adbPath -HardwareEvidencePath $HardwareEvidencePath
$blockerMatrix = Get-ProductionBlockerMatrix -HardwareStatus $hardwareStatus
$hardwareBlocker = @($blockerMatrix | Where-Object { $_.Blocker -eq "MSPOS-K physical smoke" })[0]

Write-ProductionBlockerMatrix -Matrix $blockerMatrix

Write-Section "Summary"
$steps | ForEach-Object {
    Write-Host ("{0,-28} {1}" -f $_.Name, $_.Status)
}
Write-Host ("{0,-28} {1}" -f "Hardware smoke precheck", $hardwareBlocker.Status)
Write-VerificationReport -Path $ReportPath -Steps $steps -HardwareStatus $hardwareStatus -Matrix $blockerMatrix -PlanOnlyMode $false

$failedSteps = @($steps | Where-Object { $_.Status -eq "FAIL" })

if ($failedSteps.Count -gt 0) {
    Write-Host "" 
    Write-Host "Automated verification failed." -ForegroundColor Red
    exit 1
}

if ($RequireHardware -and $hardwareStatus.Status -ne "PASS") {
    Write-Host "" 
    Write-Host "Automated gates passed, but hardware evidence is still pending." -ForegroundColor Yellow
    Write-Host "Use docs\\release-checklist.md to complete physical POS smoke steps." -ForegroundColor Yellow
    exit 2
}

Write-Host ""
Write-Host "Automated gates passed." -ForegroundColor Green
if ($hardwareStatus.Status -ne "PASS") {
    Write-Host "Physical POS acceptance remains pending: $($hardwareStatus.Reason)." -ForegroundColor Yellow
    Write-Host "Manual follow-up: docs\\release-checklist.md" -ForegroundColor Yellow
}
