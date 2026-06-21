Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent (Split-Path -Parent $MyInvocation.MyCommand.Path)
$scriptPath = Join-Path $repoRoot "verify-phase-b.ps1"
$source = Get-Content -Raw -Path $scriptPath

if ($source.Contains('| `$command` |')) {
    Write-Host "Report command interpolation regression failed: literal `$command is written to the markdown report." -ForegroundColor Red
    exit 1
}

Write-Host "Report command interpolation regression passed."
