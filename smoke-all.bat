@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\smoke-all.ps1" %*
exit /b %errorlevel%
