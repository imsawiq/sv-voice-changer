@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\smoke-version.ps1" %*
exit /b %errorlevel%
