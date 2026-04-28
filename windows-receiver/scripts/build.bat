@echo off
REM ============================================================
REM  Wameed - Build Wameed.exe + Installer (uses wameed.spec)
REM  Output:
REM    dist\Wameed.exe
REM    installer\Output\WameedSetup-1.0.0.exe
REM ============================================================
setlocal EnableDelayedExpansion
set ROOT=%~dp0..
cd /d "%ROOT%"

echo =================================================
echo  Wameed - Building exe + installer
echo =================================================

echo.
echo [1/4] Installing build dependencies...
pip install -r src\requirements.txt
pip install "pyinstaller>=6.0"

echo.
echo [2/4] Cleaning previous build...
if exist build rmdir /s /q build
if exist dist  rmdir /s /q dist

echo.
echo [3/4] Building Wameed.exe via wameed.spec ...
pyinstaller --noconfirm wameed.spec
if not exist "dist\Wameed.exe" (
  echo [FAIL] PyInstaller did not produce dist\Wameed.exe
  exit /b 1
)
echo [OK] dist\Wameed.exe

echo.
echo [4/4] Compiling Inno Setup installer ...

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
REM Installer succeeded - dist/Wameed.exe is now redundant; keep only the installer.
if exist build rmdir /s /q build
if exist dist  rmdir /s /q dist

echo.
echo =================================================
echo  [DONE] Installer: installer\Output\WameedSetup-1.0.0.exe
echo =================================================
exit /b 0