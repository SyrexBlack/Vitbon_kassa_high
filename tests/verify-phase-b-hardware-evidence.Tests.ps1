Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$scriptPath = Join-Path $repoRoot "verify-phase-b.ps1"
$source = Get-Content -Raw -Path $scriptPath

$adbPassPattern = 'if\s*\(\$onlineDevices\.Count\s+-gt\s+0\)[\s\S]*?Status\s*=\s*"PASS"'
if ($source -match $adbPassPattern) {
    Write-Host "Hardware evidence regression failed: adb online device detection can mark MSPOS-K smoke PASS without explicit evidence." -ForegroundColor Red
    exit 1
}

if (-not $source.Contains("HardwareEvidencePath")) {
    Write-Host "Hardware evidence regression failed: verifier lacks an explicit HardwareEvidencePath gate." -ForegroundColor Red
    exit 1
}

Write-Host "Hardware evidence regression passed."
