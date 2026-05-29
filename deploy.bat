@echo off
chcp 65001 > nul
title MqlAnalyser Deployment
echo =======================================================================
echo   MqlAnalyser - Auto-Deployment & Version Increment
echo =======================================================================
echo.

powershell -NoProfile -ExecutionPolicy Bypass -File deploy.ps1

if %errorlevel% neq 0 (
    echo.
    echo [FEHLER] Deployment failed.
    goto :end
)

echo.
echo [INFO] Deployment successful.

:end
echo.
pause
