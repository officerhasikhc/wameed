@echo off
REM ============================================================
REM  Wameed - DEBUG build (console visible) for diagnosing
REM           silent crashes of Wameed.exe.
REM  Output: dist\Wameed.exe (with console)
REM ============================================================
setlocal
set ROOT=%~dp0..
cd /d "%ROOT%"

echo [1/2] Installing deps...
pip install -r src\requirements.txt
pip install "pyinstaller>=6.0"

echo [2/2] Building debug exe (console visible)...
if exist build rmdir /s /q build
if exist dist  rmdir /s /q dist

pyinstaller --onefile --console --name Wameed --icon "src\wameed.ico" ^
  --add-data "src\wameed.ico;." ^
  --hidden-import websockets.legacy.server ^
  --hidden-import websockets.legacy.protocol ^
  --collect-submodules websockets ^
  src\receiver.py

echo.
if exist "dist\Wameed.exe" (
  echo [OK] Debug exe at dist\Wameed.exe
  echo Run it from a cmd prompt to see live Python errors:
  echo    dist\Wameed.exe
) else (
  echo [FAIL] build failed
  exit /b 1
)