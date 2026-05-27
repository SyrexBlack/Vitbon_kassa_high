param(
    [switch]$RequireHardware,
    [switch]$SkipRelease
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendJavaHome = "C:\Program Files\Java\jdk-17"
$adbPath = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"

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
    param([string]$AdbExecutable)

    Write-Section "ADB hardware detection"

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
        Write-Host "Result  : PASS ($($onlineDevices.Count) device(s) online)"
        return [pscustomobject]@{
            Status = "PASS"
            Reason = "physical device detected"
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

$backendPathValue = if ($env:Path) {
    "$backendJavaHome\bin;$($env:Path)"
} else {
    "$backendJavaHome\bin"
}

$steps = @(
    (Invoke-VerificationStep -Name "Backend tests" -WorkingDirectory (Join-Path $repoRoot "backend") -Command ".\gradlew.bat test --no-daemon --console=plain" -Environment @{ JAVA_HOME = $backendJavaHome; Path = $backendPathValue })

    (Invoke-VerificationStep -Name "Android unit + debug assemble" -WorkingDirectory (Join-Path $repoRoot "android") -Command ".\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --no-daemon --console=plain")
)

if (-not $SkipRelease) {
    $steps += (Invoke-VerificationStep -Name "Android release assemble" -WorkingDirectory (Join-Path $repoRoot "android") -Command ".\gradlew.bat :app:assembleRelease --no-daemon --console=plain")
}

$hardwareStatus = Get-HardwareStatus -AdbExecutable $adbPath

Write-Section "Summary"
$steps | ForEach-Object {
    Write-Host ("{0,-28} {1}" -f $_.Name, $_.Status)
}
Write-Host ("{0,-28} {1}" -f "Hardware smoke precheck", $hardwareStatus.Status)

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
