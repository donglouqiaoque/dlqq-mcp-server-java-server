@echo off
setlocal EnableExtensions
chcp 65001 >nul
cd /d "%~dp0"

REM ============================================================
REM dlqq-mcp-server - HTTP launcher (bundled runtime)
REM
REM Layout (all relative to this file):
REM   start-http.bat
REM   dlqq_mcp_server.jar
REM   libs\*.jar
REM   java\bin\java.exe
REM   python\python.exe
REM
REM Double-click to start.
REM
REM Usage:
REM   start-http.bat [nopause]
REM
REM   nopause  skip the final pause (used by AI automation)
REM
REM RULE: this file must stay ASCII-only.
REM ============================================================

set "NOPAUSE=0"
:parse
if "%~1"=="" goto parsed
if /i "%~1"=="nopause"  ( set "NOPAUSE=1" & shift & goto parse )
if /i "%~1"=="-nopause" ( set "NOPAUSE=1" & shift & goto parse )
if /i "%~1"=="/nopause" ( set "NOPAUSE=1" & shift & goto parse )
shift & goto parse
:parsed

set "JAR=dlqq_mcp_server.jar"
set "JAVA=java\bin\java.exe"
set "LIBS=libs"
set "PYTHON=python\python.exe"

if not exist "%JAR%" (
    echo [ERROR] %JAR% not found next to this script.
    echo         The release package is incomplete.
    call :do_pause
    exit /b 1
)
if not exist "%JAVA%" (
    echo [ERROR] Bundled Java runtime not found: %JAVA%
    echo         The release package is incomplete.
    call :do_pause
    exit /b 1
)
if not exist "%LIBS%\" (
    echo [ERROR] Dependency directory not found: %LIBS%\
    echo         The release package is incomplete.
    call :do_pause
    exit /b 1
)
if not exist "%PYTHON%" (
    echo [ERROR] Bundled Python runtime not found: %PYTHON%
    echo         The release package is incomplete.
    call :do_pause
    exit /b 1
)

:ask_workspace
set "WORKSPACE="
echo.
echo ============================================================
echo  Workspace
echo  Enter an existing absolute directory for the MCP workspace.
echo  Press ENTER to use the current directory:
echo    %CD%
echo ============================================================
set /p "WORKSPACE=Workspace path: "
if not defined WORKSPACE goto use_cwd
set "WORKSPACE=%WORKSPACE:"=%"
if not defined WORKSPACE goto use_cwd
if not exist "%WORKSPACE%\" (
    echo [ERROR] Directory does not exist: %WORKSPACE%
    echo.
    goto ask_workspace
)
set "WS_ARG=--workspace "%WORKSPACE%""
goto workspace_ready

:use_cwd
set "WORKSPACE=%CD%"
set "WS_ARG="
echo Using current directory as workspace.

:workspace_ready
echo.
echo ============================================================
echo  dlqq-mcp-server  -  HTTP mode
echo  Endpoint  : http://localhost:49129/mcp
echo  Workspace : %WORKSPACE%
echo  Java      : %CD%\%JAVA%
echo  Libs      : %CD%\%LIBS%
echo ============================================================
echo.

"%JAVA%" -Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 -jar "%JAR%" --transport http --port 49129 --path /mcp %WS_ARG%

set "RC=%ERRORLEVEL%"
echo.
echo Server stopped. Exit code: %RC%
call :do_pause
endlocal
exit /b %RC%

:do_pause
if "%NOPAUSE%"=="0" pause
exit /b 0
