@echo off
setlocal
set "SCRIPT_DIR=%~dp0"

where node >nul 2>nul
if errorlevel 1 (
  echo node not found in PATH. Install Node.js, then rerun. 1>&2
  exit /b 1
)

node "%SCRIPT_DIR%verify-mvp.mjs" %*
exit /b %ERRORLEVEL%
