$ErrorActionPreference = "Stop"

$root = $PSScriptRoot
Set-Location $root

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

$versionProps = Read-VersionProperties "$root\version.properties"
$version = [string]$versionProps["versionName"]
if ([string]::IsNullOrWhiteSpace($version)) {
    throw "version.properties is missing versionName"
}

Write-Host "[pre] Syncing and verifying version files..." -ForegroundColor Yellow
& "$root\scripts\sync-version.ps1"
& "$root\scripts\verify-version.ps1"

Write-Host ""
Write-Host "Building Wameed Windows v$version only..." -ForegroundColor Cyan

Get-Process "Wameed" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 1

# Keep Android outputs untouched, but remove stale Windows outputs.
Remove-Item -Recurse -Force "$root\windows-receiver\build" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\windows-receiver\dist" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\windows-receiver\installer\Output" -ErrorAction SilentlyContinue

& "$root\windows-receiver\scripts\build.bat"
if ($LASTEXITCODE -ne 0) {
    throw "Windows build failed."
}

$releaseDir = "$root\release"
New-Item -ItemType Directory -Force $releaseDir | Out-Null

# Keep one Windows artifact in release. Do not delete the Android APK.
Remove-Item "$releaseDir\WameedSetup-*.exe" -Force -ErrorAction SilentlyContinue
Remove-Item "$releaseDir\Wameed.exe" -Force -ErrorAction SilentlyContinue

$installer = "$root\windows-receiver\installer\Output\WameedSetup-$version.exe"

if (Test-Path -LiteralPath $installer) {
    Copy-Item $installer $releaseDir
    $artifact = "$releaseDir\WameedSetup-$version.exe"
} else {
    # بنية onedir: لا يوجد exe مستقل — المثبّت هو الناتج الوحيد القابل للتوزيع.
    throw "Windows build finished, but no Wameed installer was found (onedir build requires Inno Setup)."
}

& "$root\scripts\verify-version.ps1"

Write-Host ""
Write-Host "Windows artifact ready:" -ForegroundColor Green
Write-Host "  $artifact" -ForegroundColor White
