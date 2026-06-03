param(
    [string]$Reports = ""
)

$ErrorActionPreference = "Stop"

$scriptParent = Split-Path -Parent $PSScriptRoot
$projectRoot = Split-Path -Parent $scriptParent
if ([string]::IsNullOrWhiteSpace($Reports)) {
    $localReports = Join-Path $scriptParent "TestLogs\Reports"
    $projectReports = Join-Path $projectRoot "Evidence\TestLogs\Reports"
    if (Test-Path -LiteralPath $localReports) {
        $Reports = $localReports
    } else {
        $Reports = $projectReports
    }
}

$summaryPath = Join-Path $Reports "thesis_sessions_summary.csv"
$samplesPath = Join-Path $Reports "thesis_samples.csv"
$sourceRoot = Split-Path -Parent $Reports

$ok = $true

function Write-Check {
    param([string]$Name, [bool]$Passed, [string]$Detail = "")
    $status = if ($Passed) { "PASS" } else { "FAIL" }
    Write-Host ("[{0}] {1} {2}" -f $status, $Name, $Detail)
}

$summaryExists = Test-Path -LiteralPath $summaryPath
$samplesExists = Test-Path -LiteralPath $samplesPath
$rawLogs = @(Get-ChildItem -LiteralPath $sourceRoot -Filter "*_user_position_history.json" -File -ErrorAction SilentlyContinue)
Write-Check "raw copied JSON logs exist" ($rawLogs.Count -gt 0) "count=$($rawLogs.Count)"
Write-Check "session summary exists" $summaryExists $summaryPath
Write-Check "sample table exists" $samplesExists $samplesPath
$ok = $ok -and ($rawLogs.Count -gt 0) -and $summaryExists -and $samplesExists

if ($summaryExists) {
    $rows = @(Import-Csv -LiteralPath $summaryPath)
    $evaluationRows = @($rows | Where-Object {
        $value = 0
        [int]::TryParse("$($_.evaluationSamples)", [ref]$value) -and $value -gt 0
    })
    Write-Check "nonzero evaluation samples" ($evaluationRows.Count -gt 0) "sessions=$($evaluationRows.Count)"
    $ok = $ok -and ($evaluationRows.Count -gt 0)
}

$pathSvgs = @(Get-ChildItem -LiteralPath $Reports -Filter "*_path.svg" -File -ErrorAction SilentlyContinue)
$errorSvgs = @(Get-ChildItem -LiteralPath $Reports -Filter "*_error.svg" -File -ErrorAction SilentlyContinue)
Write-Check "path SVGs generated" ($pathSvgs.Count -gt 0) "count=$($pathSvgs.Count)"
$requiresErrorSvg = $summaryExists -and $evaluationRows.Count -gt 0
$errorSvgCheck = if ($requiresErrorSvg) { $errorSvgs.Count -gt 0 } else { $true }
Write-Check "error SVGs generated when evaluation exists" $errorSvgCheck "count=$($errorSvgs.Count)"
$ok = $ok -and ($pathSvgs.Count -gt 0) -and $errorSvgCheck

$figureDir = Join-Path $projectRoot "Documentation\figures\svg"
$architecture = Join-Path $figureDir "system_architecture.svg"
$layout = Join-Path $figureDir "current_room_beacon_layout.svg"
Write-Check "architecture figure exists" (Test-Path -LiteralPath $architecture) $architecture
Write-Check "room layout figure exists" (Test-Path -LiteralPath $layout) $layout
$ok = $ok -and (Test-Path -LiteralPath $architecture) -and (Test-Path -LiteralPath $layout)

if (-not $ok) {
    Write-Host ""
    Write-Host "Delivery is not evidence-complete yet. Collect controlled static or externally annotated ground-truth trials and regenerate reports."
    exit 1
}

Write-Host ""
Write-Host "Delivery evidence checks passed."
