param([switch]$RequireArtifacts)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$versionFile = Join-Path $root "version.properties"

function Read-VersionProperties {
    param([string]$Path)
    $props = @{}
    foreach ($rawLine in Get-Content -LiteralPath $Path -Encoding UTF8) {
        $line = $rawLine.Trim()
        if (-not $line -or $line.StartsWith("#")) { continue }
        $idx = $line.IndexOf("=")
        if ($idx -lt 1) { continue }
        $props[$line.Substring(0, $idx).Trim()] = $line.Substring($idx + 1).Trim()
    }
    return $props
}

function Find-Aapt {
    $roots = New-Object System.Collections.Generic.List[string]
    $roots.Add((Join-Path $env:LOCALAPPDATA "Android\Sdk\build-tools"))
    if ($env:ANDROID_HOME) { $roots.Add((Join-Path $env:ANDROID_HOME "build-tools")) }
    if ($env:ANDROID_SDK_ROOT) { $roots.Add((Join-Path $env:ANDROID_SDK_ROOT "build-tools")) }

    foreach ($sdkRoot in ($roots | Where-Object { $_ -and (Test-Path -LiteralPath $_) } | Select-Object -Unique)) {
        $aapt = Get-ChildItem -Path $sdkRoot -Recurse -Filter "aapt.exe" -ErrorAction SilentlyContinue |
            Sort-Object FullName -Descending |
            Select-Object -First 1
        if ($aapt) { return $aapt.FullName }
    }
    return $null
}

$props = Read-VersionProperties $versionFile
$versionName = [string]$props["versionName"]
$versionCode = [int]$props["versionCode"]
$errors = New-Object System.Collections.Generic.List[string]

if ([string]::IsNullOrWhiteSpace($versionName) -or $versionCode -le 0) {
    $errors.Add("version.properties must contain versionName and a positive versionCode.")
}

$updateJsonPath = Join-Path $root "update.json"
$update = Get-Content -LiteralPath $updateJsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ([int]$update.android.versionCode -ne $versionCode) { $errors.Add("update.json android.versionCode is $($update.android.versionCode), expected $versionCode.") }
if ([string]$update.android.versionName -ne $versionName) { $errors.Add("update.json android.versionName is $($update.android.versionName), expected $versionName.") }
if ([string]$update.windows.version -ne $versionName) { $errors.Add("update.json windows.version is $($update.windows.version), expected $versionName.") }
if ($update.android.updateUrl -notmatch "/$([regex]::Escape($versionName))/") { $errors.Add("Android updateUrl does not point at release tag $versionName.") }
if ($update.windows.updateUrl -notmatch "/$([regex]::Escape($versionName))/") { $errors.Add("Windows updateUrl does not point at release tag $versionName.") }

$gradlePath = Join-Path $root "app\build.gradle.kts"
$gradle = Get-Content -LiteralPath $gradlePath -Raw -Encoding UTF8
if ($gradle -notmatch "versionPropsFile") { $errors.Add("app/build.gradle.kts must read version.properties.") }
if ($gradle -match 'versionName\s*=\s*"[^"]+"' -or $gradle -match 'versionCode\s*=\s*\d+') {
    $errors.Add("app/build.gradle.kts still appears to hardcode versionName/versionCode.")
}

$receiverPath = Join-Path $root "windows-receiver\src\receiver.py"
$receiver = Get-Content -LiteralPath $receiverPath -Raw -Encoding UTF8
if ($receiver -match 'VERSION\s*=\s*"') { $errors.Add("receiver.py still hardcodes VERSION.") }
if ($receiver -notmatch "wameed_version") { $errors.Add("receiver.py must import generated wameed_version.py.") }

$pyVersionPath = Join-Path $root "windows-receiver\src\wameed_version.py"
$pyVersion = Get-Content -LiteralPath $pyVersionPath -Raw -Encoding UTF8
if ($pyVersion -notmatch "VERSION_NAME = `"$([regex]::Escape($versionName))`"") { $errors.Add("wameed_version.py does not contain VERSION_NAME $versionName.") }
if ($pyVersion -notmatch "VERSION_CODE = $versionCode") { $errors.Add("wameed_version.py does not contain VERSION_CODE $versionCode.") }

$innoPath = Join-Path $root "windows-receiver\installer\wameed.iss"
$inno = Get-Content -LiteralPath $innoPath -Raw -Encoding UTF8
if ($inno -notmatch '#include "version\.iss"') { $errors.Add("wameed.iss must include generated version.iss.") }

$versionInfoPath = Join-Path $root "windows-receiver\version_info.txt"
$versionInfo = Get-Content -LiteralPath $versionInfoPath -Raw -Encoding UTF8
if ($versionInfo -notmatch "ProductVersion', '$([regex]::Escape($versionName))'") { $errors.Add("version_info.txt does not contain ProductVersion $versionName.") }

if ($RequireArtifacts) {
    $apkPath = Join-Path $root "release\Wameed-Android.apk"
    if (-not (Test-Path -LiteralPath $apkPath)) {
        $errors.Add("release/Wameed-Android.apk is missing.")
    } else {
        $aapt = Find-Aapt
        if (-not $aapt) {
            $errors.Add("Could not find Android SDK aapt.exe to inspect release/Wameed-Android.apk.")
        } else {
            $badging = & $aapt dump badging $apkPath 2>&1
            if ($LASTEXITCODE -ne 0) {
                $errors.Add("aapt failed to inspect release/Wameed-Android.apk: $badging")
            } else {
                $match = [regex]::Match(($badging -join "`n"), "package: name='([^']+)' versionCode='([^']+)' versionName='([^']*)'")
                if (-not $match.Success) {
                    $errors.Add("Could not parse package metadata from release/Wameed-Android.apk.")
                } else {
                    $apkPackage = $match.Groups[1].Value
                    $apkCode = [int]$match.Groups[2].Value
                    $apkName = $match.Groups[3].Value
                    if ($apkPackage -ne "com.wameed") { $errors.Add("release/Wameed-Android.apk package is $apkPackage, expected com.wameed.") }
                    if ($apkCode -ne $versionCode) { $errors.Add("release/Wameed-Android.apk versionCode is $apkCode, expected $versionCode.") }
                    if ($apkName -ne $versionName) { $errors.Add("release/Wameed-Android.apk versionName is $apkName, expected $versionName.") }
                }
            }
        }
    }
}

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    throw "Version verification failed."
}

Write-Host "Version verification passed: $versionName ($versionCode)"
if ($RequireArtifacts -and (Test-Path -LiteralPath (Join-Path $root "release\Wameed-Android.apk"))) {
    $hash = Get-FileHash -LiteralPath (Join-Path $root "release\Wameed-Android.apk") -Algorithm SHA256
    Write-Host "Android artifact SHA256: $($hash.Hash)"
}
