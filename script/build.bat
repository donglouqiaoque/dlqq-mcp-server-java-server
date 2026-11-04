@echo off
setlocal EnableExtensions

REM ============================================================
REM dlqq-mcp-server - build only (no copy)
REM Script lives in <project root>\script\ , project root = parent dir
REM Output:
REM   target\dlqq_mcp_server.jar   (thin jar, no third-party deps)
REM   target\libs\*.jar               (runtime dependencies)
REM
REM Usage:
REM   build.bat [nopause]
REM
REM   nopause  skip the final pause (used by AI automation)
REM
REM RULE: this file must stay ASCII-only. Do NOT add CJK characters
REM       to a .bat file (cmd codepage would garble them).
REM ============================================================

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
cd /d "%PROJECT_DIR%"

set "NOPAUSE=0"
:parse
if "%~1"=="" goto parsed
if /i "%~1"=="nopause"  ( set "NOPAUSE=1" & shift & goto parse )
if /i "%~1"=="-nopause" ( set "NOPAUSE=1" & shift & goto parse )
if /i "%~1"=="/nopause" ( set "NOPAUSE=1" & shift & goto parse )
shift & goto parse
:parsed

echo [INFO] ========================================
echo [INFO] Building Java project...
echo [INFO] ========================================
echo.

call mvn clean package -DskipTests

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven build failed!
    call :do_pause
    exit /b 1
)

echo.
echo [INFO] Build success!
echo [INFO] JAR : target\dlqq_mcp_server.jar
echo [INFO] Libs: target\libs
echo.
call :do_pause

endlocal
exit /b 0

:do_pause
if "%NOPAUSE%"=="0" pause
exit /b 0
