# Debug Crash Collection Script for Retarget v0.3.0-alpha
# Prerequisites:
#   - ADB installed and on PATH, or set $env:ADB_PATH
#   - USB debugging enabled on device (Developer Options → USB Debugging)
#   - Device authorized on PC

$ADB = if ($env:ADB_PATH) { "$env:ADB_PATH\adb.exe" } else { "adb.exe" }

Write-Host "=== Retarget v0.3.0-alpha Crash Diagnostics ===" -ForegroundColor Cyan
Write-Host ""

# 1. Device connectivity check
Write-Host "[1/5] Checking device connectivity..." -ForegroundColor Yellow
$devices = & $ADB devices 2>&1
if ($devices -match "offline" -or ($devices -match "List of devices attached" -and $devices.Count -eq 1)) {
    Write-Host "WARNING: Device not ready or offline. Ensure USB debugging is ON and authorize the PC." -ForegroundColor Red
}
else {
    $deviceSerial = ($devices | Where-Object { $_ -match "device$" } | ForEach-Object { ($_ -split "`t")[0] })[0]
    if (-not $deviceSerial) {
        Write-Host "No connected device found." -ForegroundColor Red
        exit 1
    }
    Write-Host "Detected device: $deviceSerial"
}

# 2. Install the APK and check version
Write-Host ""
Write-Host "[2/5] Installing retarget-v0.3.0-alpha.apk..." -ForegroundColor Yellow
$apkPath = Join-Path $PSScriptRoot "..\Desktop\retarget-v0.3.0-alpha.apk"
if (-not (Test-Path $apkPath)) {
    $apkPath = Join-Path $PSScriptRoot "..\app\build\outputs\apk\debug\app-debug.apk"
}
& $ADB install -r "$apkPath" 2>&1 | Out-String

# 3. Capture boot-complete and crash logs
Write-Host ""
Write-Host "[3/5] Clearing old logcat and launching app..." -ForegroundColor Yellow
& $ADB logcat -c 2>&1
& $ADB shell am start -a android.intent.action.MAIN -n com.retarget.app/com.retarget.app.MainActivity 2>&1

Write-Host "Waiting 15 seconds to capture startup sequence..." -ForegroundColor Yellow
Start-Sleep -Seconds 15

# 4. Extract crash/exception logs
Write-Host ""
Write-Host "[4/5] Extracting crash logs..." -ForegroundColor Yellow
$logPath = Join-Path $PSScriptRoot "..\logs\crash-diags-$(Get-Date -Format 'yyyyMMdd-HHmmss').txt"
New-Item -ItemType Directory -Force -Path (Split-Path $logPath) | Out-Null

@"
===== DEVICE INFO =====
`n
Device Model: $( & $ADB shell getprop ro.product.model | Out-String )
Android Version: $( & $ADB shell getprop ro.build.version.release | Out-String )
Build Fingerprint: $( & $ADB shell getprop ro.build.fingerprint | Out-String )
`n
===== APP VERSION =====
`n
Package Info: $( & $ADB dumpsys package com.retarget.app 2>&1 | Select-String 'versionName|versionCode' | Out-String )
`n
===== CRASH LOGS (last 300 lines) =====
`n
"@ | Out-File $logPath -Encoding utf8

& $ADB logcat -d *:E | Select-Object -Last 300 | Out-File -Append $logPath

# 5. Process Stack Trace
Write-Host ""
Write-Host "[5/5] Analyzing stack traces..." -ForegroundColor Yellow
$stackTrace = Get-Content $logPath -Raw | Select-String -Pattern '(?s)Exception.*?at .*?(?=\n\n|\n---|$)' -AllMatches
if ($stackTrace.Matches) {
    Write-Host "FOUND STACK TRACES:" -ForegroundColor Red
    foreach ($match in $stackTrace.Matches) {
        Write-Host ""
        Write-Host "--- BEGIN STACK TRACE ---" -ForegroundColor Yellow
        Write-Host $match.Value | Select-Object -First 15
        Write-Host "... (truncated)" -ForegroundColor Gray
        Write-Host "--- END STACK TRACE ---" -ForegroundColor Yellow
    }
    
    # Save full analysis report
    $analysisPath = $logPath -replace '\.txt$', '-ANALYSIS.txt'
    @"
Full Log Analysis Report
=========================
Generated: $(Get-Date)
Log Source: $logPath

Stack Traces Found: $($stackTrace.Matches.Count)
Key Exceptions:
$(foreach($m in $stackTrace.Matches){$m.Value.Split([Environment]::NewLine) | Select-String 'Exception:' | ForEach-Object { "- $_" }})

Recommendations:
- Check for NullPointerException in UI initialization (common with missing resources)
- Look for Hilt injection failures (missing @Module, wrong @Provides)
- Verify WorkManager initializers (database migrations, Room schema)
- Android 17-specific: Check for stricter foreground service restrictions
"@ | Out-File $analysisPath -Encoding utf8
    
    Write-Host "`nFull analysis saved to: $analysisPath" -ForegroundColor Green
}
else {
    Write-Host "No explicit exception stacks found. Checking ANRs..." -ForegroundColor Yellow
    & $ADB shell dumpsys activity processes | Select-String 'proc name' | Out-File -Append $logPath
}

Write-Host ""
Write-Host "=== Diagnostic collection complete ===" -ForegroundColor Cyan
Write-Host "Log files saved to:" -ForegroundColor Cyan
Write-Host "  $logPath" -ForegroundColor White
if ($stackTrace.Matches) { Write-Host "  $analysisPath" -ForegroundColor White }
