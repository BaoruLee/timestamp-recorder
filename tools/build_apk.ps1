# Build the TimestampRecorder APK locally (Windows/PowerShell, no Gradle needed).
# Requires: JDK 17 + Android SDK (platforms;android-34, build-tools;34.0.0)
# Usage:  powershell -ExecutionPolicy Bypass -File tools\build_apk.ps1
# NOTE: keep this file pure ASCII (PowerShell 5.1 reads scripts as ANSI).
$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot

# --- discover JDK 17 (prefer the locally installed one) ---
$cand = Get-ChildItem (Join-Path $env:LOCALAPPDATA "Programs\Microsoft") -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($cand) { $jdkHome = $cand.FullName }
elseif ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\javac.exe"))) { $jdkHome = $env:JAVA_HOME }
if (-not $jdkHome -or -not (Test-Path (Join-Path $jdkHome "bin\javac.exe"))) {
    throw "JDK 17 not found. Set JAVA_HOME to a JDK 17 installation."
}
$env:JAVA_HOME = $jdkHome
$env:PATH = "$jdkHome\bin;$env:PATH"
Write-Host "JDK  : $jdkHome"

# --- discover Android SDK ---
$sdkRoot = $env:ANDROID_HOME
if (-not $sdkRoot) { $sdkRoot = $env:ANDROID_SDK_ROOT }
if (-not $sdkRoot -or -not (Test-Path $sdkRoot)) { $sdkRoot = Join-Path $env:LOCALAPPDATA "Android\Sdk" }
if (-not (Test-Path $sdkRoot)) { throw "Android SDK not found. Set ANDROID_HOME." }
Write-Host "SDK  : $sdkRoot"

$bt = Join-Path $sdkRoot "build-tools\34.0.0"
$platform = Join-Path $sdkRoot "platforms\android-34\android.jar"
foreach ($p in @($bt, $platform)) { if (-not (Test-Path $p)) { throw "Missing: $p" } }

# --- mirror project to an ASCII-only temp path (aapt2 cannot open Chinese paths) ---
$work = Join-Path $env:TEMP "tsr_build_$PID"
if (Test-Path $work) { Remove-Item $work -Recurse -Force }
New-Item -ItemType Directory -Force -Path "$work\app" | Out-Null
Copy-Item (Join-Path $root "app\src") (Join-Path $work "app\src") -Recurse -Force
Write-Host "WORK : $work"

# Security: the keystore password is NOT hardcoded (this script is committed
# to the repo). It is read from $env:TSR_KEYSTORE_PASS, or prompted at runtime.
$ks = Join-Path $root "release.keystore"
$ksPass = $env:TSR_KEYSTORE_PASS
if ([string]::IsNullOrWhiteSpace($ksPass)) {
    $ksPass = Read-Host "Keystore password (alias=timestamp)"
}
$out = Join-Path $root "时间戳记录_v1.0.apk"
$build = Join-Path $work "build"
New-Item -ItemType Directory -Force -Path "$build\gen", "$build\classes", "$build\dex" | Out-Null

Write-Host "[1/7] aapt2 compile"
& "$bt\aapt2.exe" compile --dir (Join-Path $work "app\src\main\res") -o "$build\compiled.flata"
if ($LASTEXITCODE -ne 0) { throw "aapt2 compile failed" }

Write-Host "[2/7] aapt2 link"
& "$bt\aapt2.exe" link -o "$build\base.apk" `
    -I $platform `
    --manifest (Join-Path $work "app\src\main\AndroidManifest.xml") `
    -R "$build\compiled.flata" `
    --java "$build\gen" `
    --auto-add-overlay
if ($LASTEXITCODE -ne 0) { throw "aapt2 link failed" }

Write-Host "[3/7] javac"
$sources = Get-ChildItem (Join-Path $work "app\src\main\java"), "$build\gen" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
& "$jdkHome\bin\javac.exe" -source 8 -target 8 -encoding UTF-8 -bootclasspath $platform -classpath $platform -d "$build\classes" $sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Write-Host "[4/7] d8 -> dex"
$classes = Get-ChildItem "$build\classes" -Recurse -Filter "*.class" | ForEach-Object { $_.FullName }
& "$bt\d8.bat" --release --min-api 24 --lib $platform --output "$build\dex" $classes
if ($LASTEXITCODE -ne 0) { throw "d8 failed" }

Write-Host "[5/7] add classes.dex"
Push-Location "$build\dex"
& "$bt\aapt.exe" add "$build\base.apk" classes.dex 2>&1 | Out-Null
Pop-Location

Write-Host "[6/7] zipalign"
& "$bt\zipalign.exe" -f 4 "$build\base.apk" "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "zipalign failed" }

Write-Host "[7/7] sign"
if (-not (Test-Path $ks)) {
    $oldEAP = $ErrorActionPreference
    $ErrorActionPreference = "Continue"
    & "$jdkHome\bin\keytool.exe" -genkeypair -keystore $ks -alias timestamp `
        -keyalg RSA -keysize 2048 -validity 10950 `
        -storepass $ksPass -keypass $ksPass `
        -dname "CN=TimestampRecorder, OU=dev, O=dev, C=CN" 2>$null
    $ErrorActionPreference = $oldEAP
}
& "$bt\apksigner.bat" sign --ks $ks --ks-pass "pass:$ksPass" --key-pass "pass:$ksPass" --out $out "$build\aligned.apk"
if ($LASTEXITCODE -ne 0) { throw "apksigner failed" }

# cleanup temp mirror
if (Test-Path $work) { Remove-Item $work -Recurse -Force }

Write-Host ""
Write-Host "BUILD OK: $out"
$fi = Get-Item $out
Write-Host ("Size   : {0:N0} KB" -f ($fi.Length / 1KB))
