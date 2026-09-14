<#
.SYNOPSIS
    TimestampRecorder - one-click build, sign and release script.
.DESCRIPTION
    1) Build the release APK with JDK 17 (version read from app\build.gradle)
    2) zipalign + sign with release.keystore via apksigner
    3) Optionally create a git tag and a GitHub Release with the APK attached

    Security: the keystore password is NOT hardcoded (this script is committed
    to the repo). It is read from $env:TSR_KEYSTORE_PASS, or prompted at runtime.

    Note: this file is intentionally ASCII-only so it needs no UTF-8 BOM.
.PARAMETER CreateRelease
    Create the git tag and GitHub Release (requires $env:GITHUB_TOKEN).
.EXAMPLE
    .\tools\release.ps1
    .\tools\release.ps1 -CreateRelease
.NOTES
    If script execution is blocked, allow it for the current process only:
        Set-ExecutionPolicy -ExecutionPolicy Bypass -Scope Process -Force
    or launch it once with:
        powershell -ExecutionPolicy Bypass -File .\tools\release.ps1
#>
param(
    [switch]$CreateRelease
)

$ErrorActionPreference = "Stop"

# ---------- paths and environment ----------
$root = Split-Path -Parent $PSScriptRoot
$jdk  = "C:\Users\Baoru Lee\AppData\Local\Programs\Microsoft\jdk-17.0.20.1+1"
$sdk  = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$bt   = Join-Path $sdk "build-tools\34.0.0"

# ANDROID_HOME is NOT persisted as a user env var on this machine, so it must be
# set here - otherwise Gradle fails with "SDK location not found".
$env:JAVA_HOME        = $jdk
$env:ANDROID_HOME     = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:PATH = "$jdk\bin;$env:PATH"

Set-Location $root
Write-Host "Project root: $root" -ForegroundColor Cyan

# ---------- version ----------
$gradle = Get-Content (Join-Path $root "app\build.gradle") -Raw
$versionName = ([regex]'versionName\s+"([^"]+)"').Match($gradle).Groups[1].Value
if ([string]::IsNullOrWhiteSpace($versionName)) { throw "Cannot parse versionName from app\build.gradle" }
Write-Host "Version: v$versionName" -ForegroundColor Cyan

# ---------- 1. build ----------
Write-Host ""
Write-Host "[1/3] Building assembleRelease ..." -ForegroundColor Yellow
& (Join-Path $root "gradlew.bat") assembleRelease
if ($LASTEXITCODE -ne 0) { throw "Build failed" }

$unsigned = Join-Path $root "app\build\outputs\apk\release\app-release-unsigned.apk"
if (-not (Test-Path $unsigned)) { throw "Output not found: $unsigned" }

# ---------- 2. align + sign ----------
Write-Host ""
Write-Host "[2/3] Aligning and signing ..." -ForegroundColor Yellow
$aligned = Join-Path $env:TEMP "tsr-aligned.apk"
$signed  = Join-Path $root "TimestampRecorder_v$versionName.apk"

$ksPass = $env:TSR_KEYSTORE_PASS
if ([string]::IsNullOrWhiteSpace($ksPass)) {
    $ksPass = Read-Host "Keystore password (alias=timestamp)"
}

& (Join-Path $bt "zipalign.exe") -f -p 4 $unsigned $aligned
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

& (Join-Path $bt "apksigner.bat") sign --ks (Join-Path $root "release.keystore") `
    --ks-key-alias timestamp --ks-pass "pass:$ksPass" --key-pass "pass:$ksPass" `
    --out $signed $aligned
if ($LASTEXITCODE -ne 0) { throw "Signing failed" }

Write-Host "Built: $signed" -ForegroundColor Green

# ---------- 2b. verify signature (release red line) ----------
# Users upgrade by overwriting the installed app, so every published APK must
# carry the release keystore signature. If signing silently fell back to the
# debug key, Android would refuse the upgrade with a signature conflict.
# Expected = release.keystore cert SHA-256 (CN=TimestampRecorder).
$expectCert = "cca83079a87053a579262dfd8db5191af36349aacd2db26b5c5c67daf8f976ce"
$prevEAP = $ErrorActionPreference
$ErrorActionPreference = "Continue"
$certOut = (& (Join-Path $bt "apksigner.bat") verify --print-certs $signed 2>&1) | Out-String
$ErrorActionPreference = $prevEAP
if ($certOut -match "SHA-256 digest:\s*([0-9a-fA-F]+)") {
    $actualCert = $Matches[1].ToLower()
} else {
    throw "Cannot read the signature of $signed - refusing to publish an unverifiable APK."
}
if ($actualCert -ne $expectCert) {
    throw "Signature mismatch: got $actualCert, expected $expectCert. The APK was not signed with release.keystore, so users could not overwrite-install it."
}
Write-Host "Signature verified: CN=TimestampRecorder ($($actualCert.Substring(0,16))...)" -ForegroundColor Green

# ---------- 3. optional: tag + GitHub Release ----------
if (-not $CreateRelease) {
    Write-Host ""
    Write-Host "[3/3] Skipped. Add -CreateRelease to tag and publish a GitHub Release." -ForegroundColor DarkGray
    Write-Host "      Manual: git tag v$versionName ; git push origin v$versionName" -ForegroundColor DarkGray
    exit 0
}

Write-Host ""
Write-Host "[3/3] Tagging and creating GitHub Release ..." -ForegroundColor Yellow
$token = $env:GITHUB_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) { throw "GITHUB_TOKEN is required to create a Release" }

git tag "v$versionName"
git push origin "v$versionName"

$headers = @{
    "Authorization" = "Bearer $token"
    "Accept"        = "application/vnd.github+json"
}
$body = @{
    tag_name = "v$versionName"
    name     = "v$versionName"
    body     = "See the commit history and the assets below."
} | ConvertTo-Json

$rel = Invoke-RestMethod -Method Post `
    -Uri "https://api.github.com/repos/baoru0908/timestamp-recorder/releases" `
    -Headers $headers -Body $body -ContentType "application/json"

$uploadUri = $rel.upload_url -replace '\{.*\}', ''
$assetName = Split-Path $signed -Leaf
Invoke-RestMethod -Method Post -Uri "$($uploadUri)?name=$assetName" `
    -Headers $headers -InFile $signed `
    -ContentType "application/vnd.android.package-archive" | Out-Null

Write-Host "Release created: $($rel.html_url)" -ForegroundColor Green
