param(
    [string]$Destination = (Join-Path $env:USERPROFILE "Downloads\ZejiosCafeReports")
)

$ErrorActionPreference = "Stop"

$sdkRoot = if ($env:ANDROID_HOME) {
    $env:ANDROID_HOME
} elseif ($env:ANDROID_SDK_ROOT) {
    $env:ANDROID_SDK_ROOT
} else {
    Join-Path $env:LOCALAPPDATA "Android\Sdk"
}

$adb = Join-Path $sdkRoot "platform-tools\adb.exe"
if (-not (Test-Path -LiteralPath $adb)) {
    throw "adb.exe was not found. Install Android platform-tools or set ANDROID_HOME."
}

New-Item -ItemType Directory -Force -Path $Destination | Out-Null

$listing = & $adb shell "ls -t /sdcard/Download/ZejiosCafe_*_Report_*.xls 2>/dev/null"
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace(($listing -join ""))) {
    throw "No Zejios Cafe report was found in the emulator Downloads folder."
}

$remoteReport = ($listing -split "`r?`n" | Where-Object { $_.Trim() } | Select-Object -First 1).Trim()
& $adb pull $remoteReport $Destination
if ($LASTEXITCODE -ne 0) {
    throw "Failed to pull $remoteReport from the emulator."
}

Write-Host "Pulled $remoteReport to $Destination"
