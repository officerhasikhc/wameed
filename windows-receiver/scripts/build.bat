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
REM Smart skip: reinstall deps only when requirements.txt changed (or clean build)
set "DEPS_HASH="
for /f "skip=1 tokens=* delims=" %%H in ('certutil -hashfile src\requirements.txt SHA256 2^>nul') do if not defined DEPS_HASH set "DEPS_HASH=%%H"
set "STORED_HASH="
if exist .pip-deps-hash set /p STORED_HASH=<.pip-deps-hash
if "%WAMEED_CLEAN%"=="1" set "STORED_HASH="
if "%DEPS_HASH%"=="%STORED_HASH%" (
  echo   [SKIP] Dependencies unchanged
) else (
  pip install -r src\requirements.txt
  pip install "pyinstaller>=6.0"
  if errorlevel 1 ( echo [FAIL] pip install failed & exit /b 1 )
  echo %DEPS_HASH%>.pip-deps-hash
)

echo.
echo [2/5] Cleaning previous build...
REM Incremental build keeps build\ (PyInstaller analysis cache); wiped only when WAMEED_CLEAN=1
if "%WAMEED_CLEAN%"=="1" (
  if exist build rmdir /s /q build
  if exist dist  rmdir /s /q dist
) else (
  echo   [KEEP] PyInstaller cache retained for incremental build
)

echo.
echo [3/5] Building Wameed (onedir) via wameed.spec ...
pyinstaller --noconfirm wameed.spec
if not exist "dist\Wameed\Wameed.exe" (
  echo [FAIL] PyInstaller did not produce dist\Wameed\Wameed.exe
  exit /b 1
)
echo [OK] dist\Wameed\Wameed.exe

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
REM Keep build\ (PyInstaller analysis cache) so the next incremental build is fast.
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
