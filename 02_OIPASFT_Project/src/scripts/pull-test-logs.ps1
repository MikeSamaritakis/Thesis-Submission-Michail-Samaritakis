#To execute this script run from the project root
# powershell -ExecutionPolicy Bypass -File .\scripts\pull-test-logs.ps1
# to copy the log files created in the device by the

param(
    [string]$Package = "com.example.connectiontest",
    [string]$Destination = "",
    [string]$Device = "",
    [switch]$Watch,
    [int]$IntervalSeconds = 5
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
if ([string]::IsNullOrWhiteSpace($Destination)) {
    $Destination = Join-Path $repoRoot "TestLogs"
}

$adb = "adb"
$sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
if (Test-Path $sdkAdb) {
    $adb = $sdkAdb
}

$remoteDir = "/storage/emulated/0/Android/data/$Package/files/TestLogs"

function Invoke-Adb {
    param([string[]]$Arguments)

    $allArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($Device)) {
        $allArgs += @("-s", $Device)
    }
    $allArgs += $Arguments

    & $adb @allArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed with exit code $LASTEXITCODE"
    }
}

function Test-RemoteDirectory {
    param([string]$Path)

    $allArgs = @()
    if (-not [string]::IsNullOrWhiteSpace($Device)) {
        $allArgs += @("-s", $Device)
    }
    $allArgs += @("shell", "if", "[", "-d", $Path, "];", "then", "echo", "exists;", "else", "echo", "missing;", "fi")

    $result = & $adb @allArgs
    if ($LASTEXITCODE -ne 0) {
        throw "adb failed with exit code $LASTEXITCODE"
    }
    return ($result -join "`n").Trim() -eq "exists"
}

function Sync-TestLogs {
    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    Write-Host "Pulling JSON logs from device..."
    Write-Host "  remote: $remoteDir"
    Write-Host "  local : $Destination"
    if (-not (Test-RemoteDirectory $remoteDir)) {
        Write-Warning "No remote TestLogs directory exists yet. Open the app and let it create at least one session log, then run this again."
        return
    }
    Invoke-Adb @("pull", "$remoteDir/.", $Destination)
}

if ($Watch) {
    Write-Host "Watching device logs. Press Ctrl+C to stop."
    while ($true) {
        try {
            Sync-TestLogs
        } catch {
            Write-Warning $_
        }
        Start-Sleep -Seconds $IntervalSeconds
    }
}

Sync-TestLogs
