Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$reportPath = Join-Path $repoRoot ".omo\ulw-loop\evidence\G002-prodverify-test-report.md"
$reportDirectory = Split-Path -Parent $reportPath

New-Item -ItemType Directory -Force -Path $reportDirectory | Out-Null
if (Test-Path $reportPath) {
    Remove-Item -Force -Path $reportPath
}

Push-Location $repoRoot
try {
    $output = & powershell -NoProfile -ExecutionPolicy Bypass -File ".\verify-phase-b.ps1" -PlanOnly -SkipRelease -ReportPath ".\.omo\ulw-loop\evidence\G002-prodverify-test-report.md" 2>&1
    $exitCode = $LASTEXITCODE
} finally {
    Pop-Location
}

$stdout = $output | Out-String
$report = if (Test-Path $reportPath) { Get-Content -Raw -Path $reportPath } else { "" }
$combined = "$stdout`n$report"

$failures = New-Object System.Collections.Generic.List[string]

if ($exitCode -ne 0) {
    $failures.Add("Expected verify-phase-b.ps1 -PlanOnly to exit 0, got $exitCode.")
}

if (-not (Test-Path $reportPath)) {
    $failures.Add("Expected report file to be created at $reportPath.")
}

$expectedText = @(
    "Production blocker matrix",
    "Fiscal adapters",
    "RBAC/security routes",
    "Encrypted local security",
    "Root policy",
    "Audit trail",
    "Status gating",
    "Backend Java 17 reports",
    "MSPOS-K physical smoke",
    "HARDWARE_REQUIRED"
)

foreach ($text in $expectedText) {
    if (-not $combined.Contains($text)) {
        $failures.Add("Expected output or report to contain '$text'.")
    }
}

if ($failures.Count -gt 0) {
    Write-Host "Plan-only production verification regression failed:" -ForegroundColor Red
    $failures | ForEach-Object { Write-Host " - $_" -ForegroundColor Red }
    Write-Host ""
    Write-Host "Captured verifier output:"
    Write-Host $stdout
    exit 1
}

Write-Host "Plan-only production verification regression passed."
