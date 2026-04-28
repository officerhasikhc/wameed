@echo off
REM Dev runner with visible console for live logs.
setlocal
set ROOT=%~dp0..
cd /d "%ROOT%\src"
echo [Wameed] Installing/updating requirements...
pip install -r requirements.txt
echo [Wameed] Launching receiver.py ...
python receiver.py
pause