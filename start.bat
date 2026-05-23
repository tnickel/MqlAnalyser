@echo off
title MqlRealMonitor Launcher
echo ===================================================
echo MqlRealMonitor - Signal Provider Analyzer Launcher
echo ===================================================
echo.
echo Kompiliere und starte Anwendung mit Maven...
echo.

:: Führe Maven-Befehl aus
call mvn compile exec:java -Dexec.mainClass="SignalProviderTable"

:: Falls der vorherige Befehl fehlschlägt, versuche den JavaFX-Plugin-Start
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [WARNUNG] Start ueber exec:java fehlgeschlagen oder unterbrochen.
    echo Versuche alternativen Start ueber javafx:run...
    echo.
    call mvn javafx:run
)

echo.
echo ===================================================
echo Anwendung wurde beendet.
echo ===================================================
pause
