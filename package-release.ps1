# ╔═══════════════════════════════════════════════════════════════════╗
# ║  package-release.ps1                                              ║
# ║  يبني كل شيء + يجمعه في Wameed-v$version.zip للأصدقاء              ║
# ║                                                                   ║
# ║  الاستخدام: من PowerShell في مجلد المشروع:                         ║
# ║      .\package-release.ps1                                        ║
# ╚═══════════════════════════════════════════════════════════════════╝

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

# Single source of truth for release version. Must match `AppVersion` in
# windows-receiver\installer\wameed.iss — if you bump one, bump the other.
$version = "1.8.2"

Write-Host ""
Write-Host "┌─────────────────────────────────────────────────┐" -ForegroundColor Cyan
Write-Host "│  بناء حزمة وميض v$version                          │" -ForegroundColor Cyan
Write-Host "└─────────────────────────────────────────────────┘" -ForegroundColor Cyan
Write-Host ""

# ─── [0/4] تنظيف الملفات القديمة ────────────────────────────────────
Write-Host "[0/4] تنظيف الملفات القديمة لضمان الحداثة..." -ForegroundColor Yellow

# إغلاق البرنامج إذا كان يعمل لتجنب Access is denied
Get-Process "Wameed" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 1 # انتظار لحظة للتأكد من إغلاق الملفات

Remove-Item -Recurse -Force "$root\release" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\windows-receiver\dist" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\windows-receiver\installer\Output" -ErrorAction SilentlyContinue
Remove-Item -Force "$root\Wameed-v$version.zip" -ErrorAction SilentlyContinue

# ─── [1/4] بناء PC (exe + installer) ────────────────────────────────
Write-Host "[1/4] بناء برنامج PC..." -ForegroundColor Yellow
& "$root\windows-receiver\scripts\build.bat" | Out-Null
if ($LASTEXITCODE -ne 0) { Write-Host "❌ فشل بناء PC" -ForegroundColor Red; exit 1 }
Write-Host "      ✓ تم بناء WameedSetup-$version.exe" -ForegroundColor Green

# ─── [2/4] بناء Android APK (Release Signed) ────────────────────────
Write-Host "[2/4] بناء APK (نسخة Release)..." -ForegroundColor Yellow
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$root\gradlew.bat" :app:assembleRelease --console=plain --quiet
if ($LASTEXITCODE -ne 0) { Write-Host "❌ فشل بناء APK" -ForegroundColor Red; exit 1 }
Write-Host "      ✓ تم بناء app-release.apk (موقّع)" -ForegroundColor Green

# ─── [3/4] جمع الملفات في release\ ──────────────────────────────────
Write-Host "[3/4] تجميع الملفات في release\..." -ForegroundColor Yellow
Remove-Item -Recurse -Force "$root\release" -ErrorAction SilentlyContinue
New-Item -ItemType Directory "$root\release" | Out-Null
Copy-Item "$root\windows-receiver\installer\Output\WameedSetup-$version.exe" "$root\release\"
Copy-Item "$root\app\build\outputs\apk\release\app-release.apk" "$root\release\Wameed-Android.apk"
Copy-Item "$root\INSTALL-للصديق.txt" "$root\release\" -ErrorAction SilentlyContinue
Write-Host "      ✓ تم نسخ المثبّت + APK + التعليمات" -ForegroundColor Green

# ─── [4/4] ضغط ZIP ─────────────────────────────────────────────────
Write-Host "[4/4] ضغط Wameed-v$version.zip..." -ForegroundColor Yellow
Compress-Archive -Path "$root\release\*" -DestinationPath "$root\Wameed-v$version.zip" -Force
$zipSize = [math]::Round((Get-Item "$root\Wameed-v$version.zip").Length / 1MB, 2)
Write-Host "      ✓ تم إنشاء ZIP بحجم $zipSize MB" -ForegroundColor Green

# ─── ملخص ─────────────────────────────────────────────────────────
Write-Host ""
Write-Host "═════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  ✅ الحزمة جاهزة!" -ForegroundColor Green
Write-Host "═════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Write-Host "  📦 الملف: $root\Wameed-v$version.zip" -ForegroundColor White
Write-Host "  📏 الحجم: $zipSize MB" -ForegroundColor White
Write-Host ""
Write-Host "  لفتح المجلد:" -ForegroundColor Gray
Write-Host "      explorer.exe /select, `"$root\Wameed-v$version.zip`"" -ForegroundColor Gray
Write-Host ""
