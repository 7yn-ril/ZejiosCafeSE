param(
    [string]$AvdName = "Pixel_Tablet",
    [switch]$SkipBuild,
    [switch]$ColdBoot,
    [int]$DeviceAppearTimeoutSeconds = 360,
    [int]$BootTimeoutSeconds = 360
)

$ErrorActionPreference = "Stop"

function Add-ToPathFront {
    param([string]$Entry)

    $segments = $env:Path -split ";" | Where-Object { $_ }
    if ($segments -contains $Entry) {
        return
    }
    $env:Path = (@($Entry) + $segments) -join ";"
}

function Get-AdbDeviceRows {
    param([string]$AdbPath)

    return (& $AdbPath devices) |
        Select-String "^emulator-" |
        ForEach-Object {
            $parts = ($_ -split "\s+") | Where-Object { $_ }
            [PSCustomObject]@{
                Serial = $parts[0]
                State = $parts[1]
            }
        }
}

function Get-AvdNameForSerial {
    param(
        [string]$AdbPath,
        [string]$Serial
    )

    try {
        $consoleNameLines = @(
            (& $AdbPath -s $Serial emu avd name 2>$null | Out-String) -split "`r?`n" |
                Where-Object { $_ -and $_ -notmatch "^(OK|KO:)" }
        )
        if ($consoleNameLines.Count -gt 0) {
            return $consoleNameLines[0].Trim()
        }

        return ((& $AdbPath -s $Serial shell getprop ro.boot.qemu.avd_name 2>$null | Out-String).Trim())
    } catch {
        return ""
    }
}

function Stop-AvdProcesses {
    param([string]$TargetAvdName)

    Get-CimInstance Win32_Process |
        Where-Object {
            $_.Name -in @("emulator.exe", "qemu-system-x86_64.exe") -and
            $_.CommandLine -match [Regex]::Escape($TargetAvdName)
        } |
        ForEach-Object {
            Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue
        }
}

function Ensure-AdbServer {
    param([string]$AdbPath)

    & $AdbPath start-server | Out-Null
}

function Restart-AdbServer {
    param([string]$AdbPath)

    & $AdbPath kill-server | Out-Null
    Start-Sleep -Seconds 1
    & $AdbPath start-server | Out-Null
}

function Wait-ForTabletSerial {
    param(
        [string]$AdbPath,
        [string]$TargetAvdName,
        [string[]]$KnownSerials = @(),
        [int]$TimeoutSeconds = 360
    )

    $knownSerialLookup = @{}
    foreach ($serial in $KnownSerials) {
        if ($serial) {
            $knownSerialLookup[$serial] = $true
        }
    }

    $seenDeviceStates = @{}
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $deviceRows = @(Get-AdbDeviceRows -AdbPath $AdbPath)

        foreach ($device in $deviceRows) {
            $seenDeviceStates[$device.Serial] = $device.State
            if (-not $knownSerialLookup.ContainsKey($device.Serial)) {
                return $device
            }
        }

        foreach ($device in $deviceRows) {
            if ($device.State -ne "device") {
                continue
            }

            $resolvedAvdName = Get-AvdNameForSerial -AdbPath $AdbPath -Serial $device.Serial
            if ($resolvedAvdName -eq $TargetAvdName) {
                return $device
            }
        }

        Start-Sleep -Seconds 5
    }

    $seenSummary = if ($seenDeviceStates.Count -gt 0) {
        ($seenDeviceStates.GetEnumerator() |
            Sort-Object Name |
            ForEach-Object { "$($_.Name) [$($_.Value)]" }) -join ", "
    } else {
        "none"
    }

    throw "Timed out waiting for emulator '$TargetAvdName' to appear in adb. Seen devices: $seenSummary"
}

function Wait-ForBootComplete {
    param(
        [string]$AdbPath,
        [string]$Serial,
        [int]$TimeoutSeconds = 360
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        $state = (& $AdbPath -s $Serial get-state 2>$null | Out-String).Trim()
        $boot = (& $AdbPath -s $Serial shell getprop sys.boot_completed 2>$null | Out-String).Trim()
        if ($state -eq "device" -and $boot -eq "1") {
            return
        }
        Start-Sleep -Seconds 5
    }

    throw "Timed out waiting for emulator '$Serial' to finish booting."
}

function Get-LogTail {
    param(
        [string]$Path,
        [int]$LineCount = 40
    )

    if (-not $Path -or -not (Test-Path $Path)) {
        return @()
    }

    return @(Get-Content $Path -Tail $LineCount -ErrorAction SilentlyContinue)
}

function Get-FreeSpaceSummary {
    param([string]$Path)

    if (-not $Path) {
        return ""
    }

    try {
        $resolvedPath = Resolve-Path $Path -ErrorAction SilentlyContinue
        $targetPath = if ($resolvedPath) { $resolvedPath.Path } else { $Path }
        $root = [System.IO.Path]::GetPathRoot($targetPath)
        if (-not $root) {
            return ""
        }

        $drive = [System.IO.DriveInfo]::new($root)
        $freeGb = [math]::Round($drive.AvailableFreeSpace / 1GB, 2)
        return "Free space on $root : $freeGb GB"
    } catch {
        return ""
    }
}

function Format-EmulatorLaunchFailure {
    param(
        [string]$TargetAvdName,
        [string[]]$LogPaths,
        [string]$AvdStoragePath
    )

    $capturedLines = foreach ($logPath in $LogPaths) {
        Get-LogTail -Path $logPath
    }
    $nonEmptyLines = @($capturedLines | Where-Object { -not [string]::IsNullOrWhiteSpace($_) })
    $relevantLines = @($nonEmptyLines | Where-Object { $_ -match "FATAL|Error:" })
    if ($relevantLines.Count -eq 0) {
        $relevantLines = @($nonEmptyLines | Select-Object -Last 10)
    }

    $message = "Emulator '$TargetAvdName' exited before it appeared in adb."
    if ($relevantLines.Count -gt 0) {
        $message += "`n" + ($relevantLines -join "`n")
    }

    if (($relevantLines -join "`n") -match "disk space") {
        $spaceSummary = Get-FreeSpaceSummary -Path $AvdStoragePath
        if ($spaceSummary) {
            $message += "`n$spaceSummary"
        }
    }

    if ($LogPaths.Count -gt 0) {
        $message += "`nLaunch logs: " + ($LogPaths -join ", ")
    }

    return $message
}

function Focus-EmulatorWindow {
    param(
        [string]$TargetAvdName,
        [string]$Serial
    )

    Add-Type @"
using System;
using System.Runtime.InteropServices;
public static class WinApi {
    [DllImport("user32.dll")] public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);
    [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr hWnd);
}
"@

    $port = $Serial -replace "^emulator-", ""
    $windowTitle = "Android Emulator - ${TargetAvdName}:$port"
    $process = Get-Process |
        Where-Object { $_.MainWindowTitle -eq $windowTitle } |
        Select-Object -First 1

    if ($process -and $process.MainWindowHandle -ne 0) {
        [WinApi]::ShowWindowAsync($process.MainWindowHandle, 9) | Out-Null
        Start-Sleep -Milliseconds 300
        [WinApi]::SetForegroundWindow($process.MainWindowHandle) | Out-Null
    }
}

function Get-RunningEmulatorWindow {
    param([string]$TargetAvdName)

    return Get-Process qemu-system-x86_64 -ErrorAction SilentlyContinue |
        Where-Object { $_.MainWindowTitle -like "Android Emulator - ${TargetAvdName}:*" } |
        Select-Object -First 1
}

function Find-ExistingTargetDevice {
    param(
        [string]$AdbPath,
        [string]$TargetAvdName,
        [int]$TimeoutSeconds = 30
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        foreach ($device in @(Get-AdbDeviceRows -AdbPath $AdbPath)) {
            $resolvedAvdName = Get-AvdNameForSerial -AdbPath $AdbPath -Serial $device.Serial
            if ($resolvedAvdName -eq $TargetAvdName) {
                return $device
            }
        }
        Start-Sleep -Seconds 2
    }

    return $null
}

function Remove-LaunchLogs {
    param([string[]]$LogPaths)

    foreach ($logPath in $LogPaths) {
        if ($logPath -and (Test-Path $logPath)) {
            Remove-Item $logPath -Force -ErrorAction SilentlyContinue
        }
    }
}

$javaHome = $env:JAVA_HOME
if (-not $javaHome -or -not (Test-Path (Join-Path $javaHome "bin\\java.exe"))) {
    $fallbackJavaHome = "C:\Program Files\Java\jdk-17"
    if (Test-Path (Join-Path $fallbackJavaHome "bin\\java.exe")) {
        $javaHome = $fallbackJavaHome
    } else {
        throw "JAVA_HOME is not set to a valid JDK. Expected JDK 17 at '$fallbackJavaHome'."
    }
}
$env:JAVA_HOME = $javaHome
Add-ToPathFront -Entry (Join-Path $javaHome "bin")

$androidSdkRoot = $env:ANDROID_SDK_ROOT
if (-not $androidSdkRoot -or -not (Test-Path $androidSdkRoot)) {
    $androidSdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk"
}
if (-not (Test-Path $androidSdkRoot)) {
    throw "ANDROID_SDK_ROOT is not set and the default SDK path '$androidSdkRoot' does not exist."
}
$env:ANDROID_SDK_ROOT = $androidSdkRoot

$adbPath = Join-Path $androidSdkRoot "platform-tools\adb.exe"
$emulatorPath = Join-Path $androidSdkRoot "emulator\emulator.exe"
$apkPath = Join-Path $PSScriptRoot "app\build\outputs\apk\debug\app-debug.apk"
$avdStoragePath = Join-Path $env:USERPROFILE ".android\avd"

if (-not (Test-Path $adbPath)) {
    throw "adb.exe was not found at '$adbPath'."
}
if (-not (Test-Path $emulatorPath)) {
    throw "emulator.exe was not found at '$emulatorPath'."
}

Write-Host "Starting adb..." -ForegroundColor Cyan
Ensure-AdbServer -AdbPath $adbPath

if (-not $SkipBuild) {
    Write-Host "Building debug APK..." -ForegroundColor Cyan
    & (Join-Path $PSScriptRoot "gradlew.bat") :app:assembleDebug --console=plain
}

if (-not (Test-Path $apkPath)) {
    throw "Debug APK not found at '$apkPath'."
}

$deviceRows = Get-AdbDeviceRows -AdbPath $adbPath
if ($deviceRows.State -contains "offline") {
    Write-Host "Found an offline emulator entry. Restarting adb and '$AvdName'..." -ForegroundColor Yellow
    Stop-AvdProcesses -TargetAvdName $AvdName
    Start-Sleep -Seconds 3
    Restart-AdbServer -AdbPath $adbPath
    $deviceRows = Get-AdbDeviceRows -AdbPath $adbPath
}

$existingTarget = Find-ExistingTargetDevice -AdbPath $adbPath -TargetAvdName $AvdName

if ($existingTarget -and $existingTarget.State -ne "device") {
    Write-Host "Found '$AvdName' in offline state. Restarting emulator..." -ForegroundColor Yellow
    Stop-AvdProcesses -TargetAvdName $AvdName
    Start-Sleep -Seconds 3
    Restart-AdbServer -AdbPath $adbPath
    $existingTarget = $null
    $deviceRows = Get-AdbDeviceRows -AdbPath $adbPath
}

$knownSerials = @($deviceRows | ForEach-Object { $_.Serial })

if (-not $existingTarget) {
    $runningWindow = Get-RunningEmulatorWindow -TargetAvdName $AvdName
    if ($runningWindow) {
        Write-Host "Detected a running emulator window for '$AvdName'. Waiting for adb to attach..." -ForegroundColor Yellow
        $existingTarget = Wait-ForTabletSerial `
            -AdbPath $adbPath `
            -TargetAvdName $AvdName `
            -KnownSerials $knownSerials `
            -TimeoutSeconds 60
    }
}

if (-not $existingTarget) {
    Write-Host "Starting emulator '$AvdName'..." -ForegroundColor Cyan
    $emulatorArgs = @("-avd", $AvdName, "-no-boot-anim")
    if ($ColdBoot) {
        $emulatorArgs += "-no-snapshot-load"
    }
    $launchStamp = Get-Date -Format "yyyyMMdd_HHmmss"
    $stdoutLogPath = Join-Path $env:TEMP "run_pixel_tablet_${AvdName}_${launchStamp}.stdout.log"
    $stderrLogPath = Join-Path $env:TEMP "run_pixel_tablet_${AvdName}_${launchStamp}.stderr.log"
    $launchLogPaths = @($stdoutLogPath, $stderrLogPath)
    $startedEmulatorProcess = Start-Process `
        -FilePath $emulatorPath `
        -ArgumentList $emulatorArgs `
        -WindowStyle Normal `
        -RedirectStandardOutput $stdoutLogPath `
        -RedirectStandardError $stderrLogPath `
        -PassThru
}

$targetDevice = if ($existingTarget) {
    $existingTarget
} else {
    $deadline = (Get-Date).AddSeconds($DeviceAppearTimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if ($startedEmulatorProcess -and $startedEmulatorProcess.HasExited) {
            throw (Format-EmulatorLaunchFailure `
                -TargetAvdName $AvdName `
                -LogPaths $launchLogPaths `
                -AvdStoragePath $avdStoragePath)
        }

        $remainingSeconds = [math]::Max([int][math]::Ceiling(($deadline - (Get-Date)).TotalSeconds), 5)
        try {
            Wait-ForTabletSerial `
                -AdbPath $adbPath `
                -TargetAvdName $AvdName `
                -KnownSerials $knownSerials `
                -TimeoutSeconds ([math]::Min($remainingSeconds, 10))
        } catch {
            if ((Get-Date) -ge $deadline) {
                throw
            }
        }
    }

    throw "Timed out waiting for emulator '$AvdName' to appear in adb."
}

Write-Host "Waiting for '$AvdName' to finish booting..." -ForegroundColor Cyan
Wait-ForBootComplete -AdbPath $adbPath -Serial $targetDevice.Serial -TimeoutSeconds $BootTimeoutSeconds

Remove-LaunchLogs -LogPaths $launchLogPaths

Write-Host "Installing APK..." -ForegroundColor Cyan
& $adbPath -s $targetDevice.Serial install -r $apkPath | Out-Host

Write-Host "Launching app..." -ForegroundColor Cyan
& $adbPath -s $targetDevice.Serial shell am start -n com.example.zejioscafese/.MainActivity | Out-Host

Focus-EmulatorWindow -TargetAvdName $AvdName -Serial $targetDevice.Serial

Write-Host ""
Write-Host "Ready on $($targetDevice.Serial) ($AvdName)." -ForegroundColor Green
