@echo off
rem ===================================================================
rem MQL Real Monitor Startscript
rem Startet MqlRealMonitor mit konfiguriertem BASE_PATH
rem ===================================================================

rem Java-Pfad setzen (relativ zum bin-Verzeichnis)
set java="..\jdk-17\bin\java.exe"

rem BASE_PATH bestimmen (ein Verzeichnis höher als bin)
rem Da das Script aus dem bin-Verzeichnis gestartet wird, ist BASE_PATH = ..
set BASE_PATH=%~dp0..

rem Normalisiere den Pfad (entferne doppelte Backslashes)
for %%i in ("%BASE_PATH%") do set BASE_PATH=%%~fi

echo ===================================================================
echo MQL Real Monitor wird gestartet...
echo BASE_PATH: %BASE_PATH%
echo Java: %java%
echo ===================================================================

rem Starte MqlRealMonitor mit BASE_PATH Parameter
%java% -jar MqlRealMonitor.jar --base-path "%BASE_PATH%" > consoleRealMonitor.txt 2> errorRealMonitor.txt

rem Prüfe Exit-Code
if %ERRORLEVEL% neq 0 (
    echo FEHLER: MqlRealMonitor wurde mit Fehlercode %ERRORLEVEL% beendet
    echo Siehe errorRealMonitor.txt für Details
    pause
) else (
    echo MqlRealMonitor erfolgreich beendet
)