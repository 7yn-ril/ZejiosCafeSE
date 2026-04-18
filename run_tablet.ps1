#Requires -Version 5.1
<#
.SYNOPSIS
    Build, install, and launch the ZejiosCafe app on any connected Android tablet
    (physical device or emulator).
.PARAMETER AvdName
    AVD to launch when no device connected (default: "Pixel_Tablet").
.PARAMETER DeviceSerial
    Directly target a specific device by serial (e.g. "emulator-5554").
.PARAMETER SkipBuild
    Skip Gradle build and use existing APK.
.PARAMETER ColdBoot
    Force cold boot of emulator (no snapshot).
.EXAMPLE
    .\run_tablet.ps1
    .\run_tablet.ps1 -SkipBuild
    .\run_tablet.ps1 -DeviceSerial emulator-5554
#>
param(
    [string]$AvdName         = "Pixel_Tablet",
    [string]$DeviceSerial    = "",
    [switch]$SkipBuild,
    [switch]$ColdBoot
)

$ErrorActionPreference = "Stop"
$appPackage   = "com.example.zejioscafese"
$mainActivity = "$appPackage/.MainActivity"

# Paths
$sdkRoot   = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$adb       = Join-Path $sdkRoot "platform-tools\adb.exe"
$emulator  = Join-Path $sdkRoot "emulator\emulator.exe"
$apk       = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
$gradlew   = Join-Path $PSScriptRoot "gradlew.bat"

if (-not (Test-Path $adb)) { throw "adb.exe not found at '$adb'." }

Write-Host ""
Write-Host "  ZejiosCafe  -  Build & Deploy" -ForegroundColor White
Write-Host "  ==============================" -ForegroundColor DarkGray
Write-Host ""

# Start ADB
Write-Host "[1/4] Starting adb server..." -ForegroundColor Cyan
& $adb start-server 2>&1 | Out-Null
Start-Sleep -Seconds 2

# Build
if ($SkipBuild) {
    Write-Host "[2/4] Skipping build (-SkipBuild)." -ForegroundColor DarkGray
} else {
    Write-Host "[2/4] Building debug APK..." -ForegroundColor Cyan
    & $gradlew clean :app:assembleDebug --console=plain
    if ($LASTEXITCODE -ne 0) {
        Write-Host "BUILD FAILED." -ForegroundColor Red
        exit 1
    }
    Write-Host "Build succeeded." -ForegroundColor Green
}

if (-not (Test-Path $apk)) {
    throw "APK not found at '$apk'. Run without -SkipBuild."
}

# Resolve device
Write-Host "[3/4] Resolving target device..." -ForegroundColor Cyan

if ($DeviceSerial) {
    $serial = $DeviceSerial
    Write-Host "Using specified device: $serial" -ForegroundColor Cyan
} else {
    # Get list of ready devices
    $lines = @(& $adb devices 2>$null)
    $devices = @()
    foreach ($line in $lines) {
        $line = $line.Trim()
        if (-not $line -or $line -match '^List of|^\*') { continue }
        $parts = $line -split '\s+' | Where-Object { $_ }
        if ($parts.Count -ge 2) {
            $d = $parts[0].Trim()
            $s = $parts[1].Trim()
            if ($d -and $s -eq "device") {
                $devices += $d
            }
        }
    }

    if ($devices.Count -eq 0) {
        # Launch emulator
        if (-not $AvdName) { throw "No device and -AvdName is empty." }
        if (-not (Test-Path $emulator)) { throw "emulator.exe not found at '$emulator'." }
        Write-Host "No device found. Launching '$AvdName' emulator..." -ForegroundColor Cyan
        $emArgs = @("-avd", $AvdName, "-no-boot-anim")
        if ($ColdBoot) { $emArgs += "-no-snapshot-load" }
        Start-Process -FilePath $emulator -ArgumentList $emArgs -WindowStyle Normal | Out-Null
        Start-Sleep -Seconds 5

        # Wait for it to appear in adb
        $deadline = (Get-Date).AddSeconds(120)
        while ((Get-Date) -lt $deadline) {
            $lines = @(& $adb devices 2>$null)
            foreach ($line in $lines) {
                $line = $line.Trim()
                if (-not $line -or $line -match '^List of|^\*') { continue }
                $parts = $line -split '\s+' | Where-Object { $_ }
                if ($parts.Count -ge 2) {
                    $d = $parts[0].Trim()
                    $s = $parts[1].Trim()
                    if ($d -and $d -like "emulator-*" -and $s -eq "device") {
                        $serial = $d
                        break
                    }
                }
            }
            if ($serial) { break }
            Start-Sleep -Seconds 3
        }
        if (-not $serial) { throw "Emulator did not appear in adb (timeout 120s)." }
    } elseif ($devices.Count -eq 1) {
        $serial = $devices[0]
        Write-Host "Using device: $serial" -ForegroundColor Cyan
    } else {
        # Multiple devices - pick first
        $serial = $devices[0]
        Write-Host "Multiple devices found. Using: $serial" -ForegroundColor Cyan
    }
}

if (-not $serial) { throw "Could not determine device serial." }

Write-Host "Waiting for device to boot..." -ForegroundColor Cyan
$deadline = (Get-Date).AddSeconds(120)
while ((Get-Date) -lt $deadline) {
    $state = (& $adb -s $serial get-state 2>&1 | Where-Object { $_ -notlike "error:*" } | Out-String).Trim()
    $boot  = (& $adb -s $serial shell getprop sys.boot_completed 2>&1 | Where-Object { $_ -notlike "error:*" } | Out-String).Trim()
    if ($state -eq "device" -and $boot -eq "1") { break }
    Start-Sleep -Seconds 2
}

# Install & Launch
Write-Host "[4/4] Installing APK on $serial..." -ForegroundColor Cyan
& $adb -s $serial install -r $apk | Out-Host
if ($LASTEXITCODE -ne 0) {
    Write-Host "Installation failed." -ForegroundColor Red
    exit 1
}

Write-Host "Launching app..." -ForegroundColor Cyan
& $adb -s $serial shell am start -n $mainActivity | Out-Host

Write-Host ""
Write-Host "  App is running on $serial" -ForegroundColor Green
Write-Host ""
