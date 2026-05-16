#Requires -Version 5.1
<#
.SYNOPSIS
    Build, install, and launch the ZejiosCafe app on any connected Android tablet
    (physical device or emulator).
.PARAMETER AvdName
    AVD to launch when no device connected (default: "Pixel_Tablet").
.PARAMETER DeviceSerial
    Directly target a specific device by serial (e.g. "emulator-5554").
.PARAMETER DnsServers
    DNS servers to use when launching an emulator (default: "8.8.8.8,1.1.1.1").
.PARAMETER MemoryMb
    RAM to give the emulator when launched by this script (default: 4096).
.PARAMETER GpuMode
    Emulator GPU mode (default: "host").
.PARAMETER SkipBuild
    Skip Gradle build and use existing APK.
.PARAMETER ColdBoot
    Kept for compatibility. The script already avoids quick-boot snapshots by default.
.PARAMETER UseSnapshot
    Allow Android Emulator quick-boot snapshots. Disabled by default because stale
    tablet snapshots can leave System UI stuck in an ANR loop.
.EXAMPLE
    .\run_tablet.ps1
    .\run_tablet.ps1 -SkipBuild
    .\run_tablet.ps1 -DeviceSerial emulator-5554
#>
param(
    [string]$AvdName         = "Pixel_Tablet",
    [string]$DeviceSerial    = "",
    [string]$DnsServers      = "8.8.8.8,1.1.1.1",
    [int]$MemoryMb           = 4096,
    [ValidateSet("auto", "host", "swiftshader_indirect", "angle_indirect")]
    [string]$GpuMode         = "host",
    [switch]$SkipBuild,
    [switch]$ColdBoot,
    [switch]$UseSnapshot
)

# Accept a common GNU-style invocation ("--coolboot") used in some shells.
if ($AvdName -eq "--coolboot") {
    $ColdBoot = $true
    $AvdName = "Pixel_Tablet"
    Write-Host "Detected '--coolboot'. Interpreting it as -ColdBoot." -ForegroundColor DarkGray
}

$ErrorActionPreference = "Stop"
$appPackage   = "com.example.zejioscafese"
$mainActivity = "$appPackage/.LoginActivity"

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
& $env:ComSpec /c "`"$adb`" start-server >nul 2>&1"
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
        $emArgs = @(
            "-avd", $AvdName,
            "-no-boot-anim",
            "-memory", $MemoryMb.ToString(),
            "-gpu", $GpuMode
        )
        if ($DnsServers) {
            $emArgs += @("-dns-server", $DnsServers)
            Write-Host "  emulator DNS servers: $DnsServers" -ForegroundColor DarkGray
        }
        if (-not $UseSnapshot) {
            $emArgs += @("-no-snapshot-load", "-no-snapshot-save")
            Write-Host "  quick-boot snapshots disabled for a clean tablet session" -ForegroundColor DarkGray
        } elseif ($ColdBoot) {
            $emArgs += "-no-snapshot-load"
        }
        Write-Host "  emulator memory: ${MemoryMb}MB, gpu: $GpuMode" -ForegroundColor DarkGray
        $emLog = Join-Path $env:TEMP "zejios_emulator.log"
        Remove-Item $emLog -ErrorAction SilentlyContinue
        $emProc = Start-Process -FilePath $emulator -ArgumentList $emArgs `
            -WindowStyle Normal -PassThru `
            -RedirectStandardOutput $emLog -RedirectStandardError "$emLog.err"
        Write-Host "  emulator pid=$($emProc.Id), log=$emLog" -ForegroundColor DarkGray
        Start-Sleep -Seconds 5

        # Wait for it to appear in adb (or die)
        $timeoutSec = 240
        $deadline = (Get-Date).AddSeconds($timeoutSec)
        while ((Get-Date) -lt $deadline) {
            if ($emProc.HasExited) {
                $stdout = if (Test-Path $emLog) { Get-Content $emLog -Raw } else { "" }
                $stderr = if (Test-Path "$emLog.err") { Get-Content "$emLog.err" -Raw } else { "" }
                Write-Host "Emulator exited early (code $($emProc.ExitCode))." -ForegroundColor Red
                if ($stdout) { Write-Host "--- stdout ---`n$stdout" -ForegroundColor DarkYellow }
                if ($stderr) { Write-Host "--- stderr ---`n$stderr" -ForegroundColor DarkYellow }
                throw "Emulator process died before adb picked it up. See output above."
            }
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
        if (-not $serial) {
            $stdout = if (Test-Path $emLog) { Get-Content $emLog -Raw } else { "" }
            if ($stdout) { Write-Host "--- emulator log ---`n$stdout" -ForegroundColor DarkYellow }
            throw "Emulator did not appear in adb (timeout ${timeoutSec}s). Check the emulator window for a boot error."
        }
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
& $adb -s $serial shell am start -S -n $mainActivity | Out-Host

Write-Host ""
Write-Host "  App is running on $serial" -ForegroundColor Green
Write-Host ""
