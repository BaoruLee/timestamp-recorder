# Verify the locally built TimestampRecorder APK.
# Checks: signature, package info, sdk versions, launcher activity, permissions (expect 0).
# Usage:  powershell -ExecutionPolicy Bypass -File tools\verify_apk.ps1
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root "时间戳记录_v1.0.apk"
if (-not (Test-Path $apk)) { throw "APK not found: $apk" }

$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }
$bt = Join-Path $sdkRoot "build-tools\34.0.0"

# aapt cannot open Chinese paths -> copy to ASCII temp path for inspection
$tmp = Join-Path $env:TEMP "tsr_verify_$PID.apk"
Copy-Item $apk $tmp -Force
try {
    Write-Host "=== 1. Signature ==="
    cmd /c "`"$bt\apksigner.bat`" verify --print-certs `"$tmp`" 2>nul" | Select-Object -First 4

    Write-Host "`n=== 2. Package info ==="
    cmd /c "`"$bt\aapt.exe`" dump badging `"$tmp`" 2>nul" |
        Select-String -Pattern "package:|sdkVersion|targetSdkVersion|application-label:|launchable-activity" |
        ForEach-Object { $_.Line }

    Write-Host "`n=== 3. Permissions (expect 0) ==="
    $perm = cmd /c "`"$bt\aapt.exe`" dump permissions `"$tmp`" 2>nul"
    $count = $perm.Count - 1
    if ($count -gt 0) { $perm } else { Write-Host "none (0 permissions)" }
}
finally {
    Remove-Item $tmp -Force -ErrorAction SilentlyContinue
}
