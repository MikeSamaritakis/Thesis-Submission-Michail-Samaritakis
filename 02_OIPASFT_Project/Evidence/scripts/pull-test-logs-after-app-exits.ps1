param(
    [string]$Package = "com.example.connectiontest",
    [string]$Destination = "",
    [string]$Device = "",
    [int]$PollSeconds = 2
)

$ErrorActionPreference = "Stop"

$adb = "adb"
$sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (Test-Path $sdkAdb) {
    $adb = $sdkAdb
}

function Invoke-AdbText {
    param([string[]]$Arguments)

    $allArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($Device)) {
        $allArgs += @("-s", $Device)
    }
    $allArgs += $Arguments

    $output = & $adb @allArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed with exit code $LASTEXITCODE"
    }
    return ($output -join "`n").Trim()
}

function Get-AppPid {
    try {
        return Invoke-AdbText @("shell", "pidof", $Package)
    } catch {
        return ""
    }
}

Write-Host "Waiting for $Package to finish..."
$sawProcess = $false
while ($true) {
    $pidText = Get-AppPid
    if (-not [string]::IsNullOrWhiteSpace($pidText)) {
        $sawProcess = $true
        Write-Host "  running pid=$pidText"
        Start-Sleep -Seconds ([Math]::Max(1, $PollSeconds))
        continue
    }

    if ($sawProcess) {
        Write-Host "App process ended. Pulling logs."
    } else {
        Write-Host "App process is not running. Pulling logs now."
    }
    break
}

$pullArgs = @(
    "-ExecutionPolicy", "Bypass",
    "-File", (Join-Path $PSScriptRoot "pull-test-logs.ps1"),
    "-Package", $Package
)
if (-not [string]::IsNullOrWhiteSpace($Destination)) {
    $pullArgs += @("-Destination", $Destination)
}
if (-not [string]::IsNullOrWhiteSpace($Device)) {
    $pullArgs += @("-Device", $Device)
}

& powershell.exe @pullArgs
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
