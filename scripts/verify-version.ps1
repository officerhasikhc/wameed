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

if ($errors.Count -gt 0) {
    $errors | ForEach-Object { Write-Error $_ }
    throw "Version verification failed."
}

Write-Host "Version verification passed: $versionName ($versionCode)"
