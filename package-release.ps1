# ╔═══════════════════════════════════════════════════════════════════╗
# ║  package-release.ps1                                              ║
# ║  يبني كل شيء + يجمعه في مجلد release\                              ║
# ║                                                                   ║
# ║  الاستخدام: من PowerShell في مجلد المشروع:                         ║
# ║      .\package-release.ps1                                        ║
# ╚═══════════════════════════════════════════════════════════════════╝

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

# Single source of truth for Android, Windows, installer, and update.json.
$versionProps = Read-VersionProperties "$root\version.properties"
$version = [string]$versionProps["versionName"]
if ([string]::IsNullOrWhiteSpace($version)) {
    throw "version.properties is missing versionName"
}

Write-Host "[pre] مزامنة ملفات الإصدار والتحقق منها..." -ForegroundColor Yellow
& "$root\scripts\sync-version.ps1"
& "$root\scripts\verify-version.ps1"

Write-Host ""
Write-Host "┌─────────────────────────────────────────────────┐" -ForegroundColor Cyan
Write-Host "│  بناء حزمة وميض v$version                          │" -ForegroundColor Cyan
Write-Host "└─────────────────────────────────────────────────┘" -ForegroundColor Cyan
Write-Host ""

# ─── [0/3] تنظيف الملفات القديمة ────────────────────────────────────
Write-Host "[0/3] تنظيف الملفات القديمة لضمان الحداثة..." -ForegroundColor Yellow

# إغلاق البرنامج إذا كان يعمل لتجنب Access is denied
Get-Process "Wameed" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 1 # انتظار لحظة للتأكد من إغلاق الملفات

Remove-Item -Recurse -Force "$root\release" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\windows-receiver\dist" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\windows-receiver\installer\Output" -ErrorAction SilentlyContinue

# ─── [1/3] بناء PC (exe + installer) ────────────────────────────────
Write-Host "[1/3] بناء برنامج PC..." -ForegroundColor Yellow
& "$root\windows-receiver\scripts\build.bat" | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "❌ فشل بناء PC" -ForegroundColor Red; exit 1 }
Write-Host "      ✓ تم بناء WameedSetup-$version.exe" -ForegroundColor Green

# ─── [2/3] بناء Android APK (Release Signed) ────────────────────────
Write-Host "[2/3] بناء APK (نسخة Release)..." -ForegroundColor Yellow
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$root\gradlew.bat" :app:assembleRelease --console=plain --quiet
if ($LASTEXITCODE -ne 0) { Write-Host "❌ فشل بناء APK" -ForegroundColor Red; exit 1 }
Write-Host "      ✓ تم بناء app-release.apk (موقّع)" -ForegroundColor Green

# ─── [3/3] جمع الملفات في release\ ──────────────────────────────────
Write-Host "[3/3] تجميع الملفات في release\..." -ForegroundColor Yellow
Remove-Item -Recurse -Force "$root\release" -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$root\release" | Out-Null
Copy-Item "$root\windows-receiver\installer\Output\WameedSetup-$version.exe" "$root\release\"
Copy-Item "$root\app\build\outputs\apk\release\app-release.apk" "$root\release\Wameed-Android.apk"
Copy-Item "$root\INSTALL-للصديق.txt" "$root\release\" -ErrorAction SilentlyContinue
Write-Host "      ✓ تم نسخ المثبّت + APK + التعليمات" -ForegroundColor Green

& "$root\scripts\verify-version.ps1"

# ─── ملخص ─────────────────────────────────────────────────────────
Write-Host ""
Write-Host "═════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  ✅ الحزمة جاهزة!" -ForegroundColor Green
Write-Host "═════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "  � المجلد: $root\release" -ForegroundColor White
Write-Host ""
Write-Host "  لفتح المجلد:" -ForegroundColor Gray
Write-Host "      explorer.exe `"$root\release`"" -ForegroundColor Gray
Write-Host ""
