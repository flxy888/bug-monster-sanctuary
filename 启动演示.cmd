@echo off
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\start-demo.ps1"
if errorlevel 1 (
  echo Startup failed. See deliverables\demo-ready\runs for logs.
  pause
  exit /b 1
)
exit /b 0
