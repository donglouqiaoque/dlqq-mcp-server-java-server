@echo off
setlocal EnableExtensions
title install dlqq-mcp-client (DSH plugin)
set "AI_MODE=0"
set "RC=0"
set "NOPAUSE=0"
set "PROFILE_DIR="

:parse
if "%~1"=="" goto run
set "ARG=%~1"
if /I "%ARG%"=="ai"   ( set "AI_MODE=1" & shift & goto parse )
if /I "%ARG%"=="-ai"  ( set "AI_MODE=1" & shift & goto parse )
if /I "%ARG%"=="--ai" ( set "AI_MODE=1" & shift & goto parse )
if /I "%ARG%"=="/ai"  ( set "AI_MODE=1" & shift & goto parse )
if "%PROFILE_DIR%"=="" set "PROFILE_DIR=%~1"
shift
goto parse

:run
if "%AI_MODE%"=="1" set "NOPAUSE=1"
set "PS_AI_ARG="
if "%AI_MODE%"=="1" set "PS_AI_ARG=-Ai"
set "PS1=%~dp0install_dlqq_mcp_dsh_client.ps1"
if not exist "%PS1%" (
    echo [X] Missing script: "%PS1%"
    set "RC=1"
    goto done
)
where powershell >nul 2>nul
if errorlevel 1 (
    echo [X] Windows PowerShell was not found.
    set "RC=1"
    goto done
)
echo ============================================================
echo  dlqq-mcp-client installer for DeepSeek Harness
echo ============================================================
echo.
echo  Close DeepSeek Harness before installing.
echo.
if defined PROFILE_DIR (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" -ProfileDir "%PROFILE_DIR%" %PS_AI_ARG%
) else (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%PS1%" %PS_AI_ARG%
)
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" (
    echo.
    echo [X] Installation failed. See messages above.
) else (
    echo.
    echo [OK] Installation finished. Restart DeepSeek Harness.
)

:done
if "%NOPAUSE%"=="0" pause
endlocal & exit /b %RC%
