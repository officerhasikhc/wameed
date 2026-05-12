@echo off
REM ============================================================
REM  Wameed - Build Wameed.exe + Installer (uses wameed.spec)
REM  Output:
REM    dist\Wameed.exe
REM    installer\Output\WameedSetup-<version>.exe
REM ============================================================
setlocal EnableDelayedExpansion
set ROOT=%~dp0..
cd /d "%ROOT%"

echo =================================================
echo  Wameed - Building exe + installer
echo =================================================

echo.
echo [0/5] Syncing version metadata...
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\..\scripts\sync-version.ps1"
if errorlevel 1 (
  echo [FAIL] Version sync failed
  exit /b 1
)
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\..\scripts\verify-version.ps1"
if errorlevel 1 (
  echo [FAIL] Version verification failed
  exit /b 1
)

echo.
echo [1/5] Installing build dependencies...
pip install -r src\requirements.txt
pip install "pyinstaller>=6.0"

echo.
echo [2/5] Cleaning previous build...
if exist build rmdir /s /q build
if exist dist  rmdir /s /q dist

echo.
echo [3/5] Building Wameed.exe via wameed.spec ...
pyinstaller --noconfirm wameed.spec
if not exist "dist\Wameed.exe" (
  echo [FAIL] PyInstaller did not produce dist\Wameed.exe
  exit /b 1
)
echo [OK] dist\Wameed.exe

echo.
echo [4/5] Compiling Inno Setup installer ...

set "ISCC="
for /f "delims=" %%I in ('where ISCC 2^>nul') do set "ISCC=%%I"
if not defined ISCC (
  if exist "C:\Program Files (x86)\Inno Setup 6\ISCC.exe" set "ISCC=C:\Program Files (x86)\Inno Setup 6\ISCC.exe"
)
if not defined ISCC (
  if exist "C:\Program Files\Inno Setup 6\ISCC.exe"       set "ISCC=C:\Program Files\Inno Setup 6\ISCC.exe"
)

if not defined ISCC (
  echo.
  echo [WARN] Inno Setup 6 not found.
  echo        Install from: https://jrsoftware.org/isdl.php
  echo        dist\Wameed.exe is ready, installer step skipped.
  exit /b 0
)

echo Using ISCC: !ISCC!
"!ISCC!" "installer\wameed.iss"
if errorlevel 1 (
  echo [FAIL] Inno Setup compile failed
  exit /b 1
)

echo.
echo [5/5] Cleaning intermediate build artifacts...
if exist build rmdir /s /q build
REM We KEEP dist/ folder because package-release.ps1 needs it

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%ROOT%\..\scripts\verify-version.ps1"
if errorlevel 1 (
  echo [FAIL] Final version verification failed
  exit /b 1
)

echo.
echo =================================================
echo  [DONE] Build Finished
echo =================================================
exit /b 0
