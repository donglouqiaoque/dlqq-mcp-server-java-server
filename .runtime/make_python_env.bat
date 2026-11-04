@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0.."

REM ============================================================
REM dlqq-mcp-server - build .runtime\python
REM
REM Asks for a full CPython installation directory, then creates a
REM portable runtime at .runtime\python with tkinter, bleak and
REM ttkbootstrap, ready for the BLE tool.
REM
REM Usage:
REM   make_python_env.bat [python_dir] [/force] [/nopip] [/dry-run] [nopause]
REM
REM   python_dir  folder that contains python.exe (asked if omitted)
REM   /force      overwrite .runtime\python without asking
REM   /nopip      copy the standard library only (no pip/bleak/ttkbootstrap)
REM   /dry-run    report what would be copied, write nothing
REM   nopause     skip the final pause (used by AI automation)
REM
REM RULE: this file must stay ASCII-only.
REM ============================================================

echo ============================================================
echo  dlqq-mcp-server - build .runtime\python
echo  tkinter + bleak + ttkbootstrap, ready for the BLE tool
echo ============================================================
echo.

set "PY_SCRIPT=.runtime\make_python_env.py"
if not exist "%PY_SCRIPT%" (
    echo [ERROR] %PY_SCRIPT% not found.
    goto fail
)

set "SRC="
set "EXTRA="
set "NOPAUSE=0"
:parse
if "%~1"=="" goto parsed
if /i "%~1"=="/force"   ( set "EXTRA=!EXTRA! --force"   & shift & goto parse )
if /i "%~1"=="/nopip"   ( set "EXTRA=!EXTRA! --no-pip"  & shift & goto parse )
if /i "%~1"=="/dry-run" ( set "EXTRA=!EXTRA! --dry-run" & shift & goto parse )
if /i "%~1"=="nopause"  ( set "NOPAUSE=1" & shift & goto parse )
if /i "%~1"=="-nopause" ( set "NOPAUSE=1" & shift & goto parse )
if /i "%~1"=="/nopause" ( set "NOPAUSE=1" & shift & goto parse )
if not defined SRC ( set "SRC=%~1" & shift & goto parse )
echo [ERROR] unknown argument: %~1
goto fail
:parsed

if not defined SRC (
    set /p "SRC=Python install dir (folder that contains python.exe): "
)
if not defined SRC (
    echo [ERROR] no path given.
    goto fail
)
set "SRC=!SRC:"=!"
if not defined SRC (
    echo [ERROR] no path given.
    goto fail
)

if exist "!SRC!\python.exe" goto src_ok
if exist "!SRC!" (
    for %%I in ("!SRC!") do set "SRC=%%~dpI"
)
:src_ok
if not exist "!SRC!\python.exe" (
    echo [ERROR] python.exe not found in: !SRC!
    echo         Point this script at the folder that contains python.exe.
    goto fail
)

echo [INFO] Python source: !SRC!
echo.

"!SRC!\python.exe" "%PY_SCRIPT%" --src "!SRC!" --out ".runtime\python" !EXTRA!
if errorlevel 1 goto fail

echo.
echo [OK] .runtime\python is ready.
echo      Test: .runtime\python\python.exe -s -P -c "import tkinter, bleak, ttkbootstrap"
call :do_pause
exit /b 0

:fail
echo.
echo [FAILED] build did not finish; existing files were left as-is.
call :do_pause
exit /b 1

:do_pause
if "%NOPAUSE%"=="0" pause
exit /b 0
