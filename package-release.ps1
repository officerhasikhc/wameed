# ╔═══════════════════════════════════════════════════════════════════╗
# ║  package-release.ps1                                              ║
# ║  يبني أندرويد وويندوز بالتوازي + يجمع الناتج في release\           ║
# ║                                                                   ║
# ║  الاستخدام:                                                       ║
# ║      .\package-release.ps1            بناء تزايدي سريع (المعتاد)  ║
# ║      .\package-release.ps1 -Clean     بناء نظيف كامل من الصفر     ║
# ╚═══════════════════════════════════════════════════════════════════╝

param(
    [switch]$Clean  # حذف كل الكاشات وإعادة تثبيت التبعيات (أبطأ، للتحقق النهائي)
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot
Set-Location $root

$swTotal = [Diagnostics.Stopwatch]::StartNew()
function Stage([string]$msg) { Write-Host "[$([int]$swTotal.Elapsed.TotalSeconds)s] $msg" -ForegroundColor Yellow }

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

# المصدر الوحيد لأرقام الإصدار للمنصتين والمثبّت وupdate.json.
$versionProps = Read-VersionProperties "$root\version.properties"
$version = [string]$versionProps["versionName"]
if ([string]::IsNullOrWhiteSpace($version)) {
    throw "version.properties is missing versionName"
}

Stage "مزامنة ملفات الإصدار والتحقق منها..."
& "$root\scripts\sync-version.ps1"
& "$root\scripts\verify-version.ps1"

Write-Host ""
Write-Host "┌─────────────────────────────────────────────────┐" -ForegroundColor Cyan
Write-Host "│  بناء حزمة وميض v$version $(if ($Clean) { '(بناء نظيف كامل)' } else { '(تزايدي)' })" -ForegroundColor Cyan
Write-Host "└─────────────────────────────────────────────────┘" -ForegroundColor Cyan
Write-Host ""

# ─── تنظيف ──────────────────────────────────────────────────────────
# إغلاق البرنامج إذا كان يعمل لتجنب Access is denied
Get-Process "Wameed" -ErrorAction SilentlyContinue | Stop-Process -Force
Start-Sleep -Seconds 1

Remove-Item -Recurse -Force "$root\release" -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "$root\windows-receiver\installer\Output" -ErrorAction SilentlyContinue
if ($Clean) {
    # البناء التزايدي يُبقي كاش PyInstaller (build\) وكاش pip — الحذف هنا فقط.
    Remove-Item -Recurse -Force "$root\windows-receiver\build" -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force "$root\windows-receiver\dist" -ErrorAction SilentlyContinue
    Remove-Item "$root\windows-receiver\.pip-deps-hash" -Force -ErrorAction SilentlyContinue
}

# ─── [1/3] بناء Android في الخلفية (بالتوازي مع بناء ويندوز) ────────
Stage "[1/3] إطلاق بناء APK بالتوازي..."
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$gradleLog = "$env:TEMP\wameed-gradle-release.log"
$gradle = Start-Process -FilePath "$root\gradlew.bat" `
    -ArgumentList ":app:assembleRelease", "--console=plain" `
    -WorkingDirectory $root -PassThru -WindowStyle Hidden `
    -RedirectStandardOutput $gradleLog -RedirectStandardError "$gradleLog.err"

# ─── [2/3] بناء PC (exe + installer) ────────────────────────────────
Stage "[2/3] بناء برنامج PC (يعمل الآن بينما يُبنى APK في الخلفية)..."
$env:WAMEED_CLEAN = if ($Clean) { "1" } else { "0" }
& "$root\windows-receiver\scripts\build.bat"
if ($LASTEXITCODE -ne 0) {
    try { Stop-Process -Id $gradle.Id -Force } catch {}
    Write-Host "❌ فشل بناء PC" -ForegroundColor Red; exit 1
}
Stage "      ✓ تم بناء WameedSetup-$version.exe"

# ─── انتظار Android ─────────────────────────────────────────────────
Stage "[1/3] انتظار اكتمال APK..."
$gradle.WaitForExit()
if ($gradle.ExitCode -ne 0) {
    Write-Host "❌ فشل بناء APK — آخر السجل:" -ForegroundColor Red
    Get-Content $gradleLog -Tail 25 -ErrorAction SilentlyContinue
    Get-Content "$gradleLog.err" -Tail 10 -ErrorAction SilentlyContinue
    exit 1
}
Stage "      ✓ تم بناء app-release.apk (موقّع)"

# ─── [3/3] جمع الملفات في release\ ──────────────────────────────────
Stage "[3/3] تجميع الملفات في release\..."
New-Item -ItemType Directory "$root\release" -Force | Out-Null
Copy-Item "$root\windows-receiver\installer\Output\WameedSetup-$version.exe" "$root\release\"
Copy-Item "$root\app\build\outputs\apk\release\app-release.apk" "$root\release\Wameed-Android.apk"
Copy-Item "$root\INSTALL-للصديق.txt" "$root\release\" -ErrorAction SilentlyContinue
Stage "      ✓ تم نسخ المثبّت + APK + التعليمات"

& "$root\scripts\verify-version.ps1" -RequireArtifacts

# ─── ملخص ─────────────────────────────────────────────────────────
$swTotal.Stop()
Write-Host ""
Write-Host "═════════════════════════════════════════════════" -ForegroundColor Green
Write-Host "  ✅ الحزمة جاهزة في $([int]$swTotal.Elapsed.TotalMinutes) د $($swTotal.Elapsed.Seconds) ث" -ForegroundColor Green
Write-Host "═════════════════════════════════════════════════" -ForegroundColor Green
Write-Host ""
Get-ChildItem "$root\release" | ForEach-Object {
    Write-Host ("  {0,-28} {1,8:N1} MB" -f $_.Name, ($_.Length / 1MB)) -ForegroundColor White
}
Write-Host ""
Write-Host "  المجلد: $root\release" -ForegroundColor White
Write-Host "  لفتح المجلد:  explorer.exe `"$root\release`"" -ForegroundColor Gray
Write-Host ""
