@echo off
:: Setze UTF-8 Codepage für korrekte Umlaute
chcp 65001 > NUL
title MqlRealMonitor Launcher

:: Ästhetisches Farbschema (Türkis auf Schwarz)
color 0B

echo =======================================================================
echo      __  ___ ____   __       ___                __                     
echo     /  ^|/  // __ \ / /      /   ^|  ____  ____ _/ /_  __  _____________  
echo    / /^|_/ // / / // /      / /^| ^| / __ \/ __ `/ / / / / / / ___/ _ \/ ___/ 
echo   / /  / // /_/ // /___   / ___ ^|/ / / / /_/ / / /_/ /_/ (__  )  __/ /     
echo  /_/  /_/ \___\_\\____/  /_/  ^|_/_/ /_/\__,_/_/\__, /\____/____/\___/_/      
echo                                               /____/                     
echo =======================================================================
echo               MQL Signal Provider Analyzer - Launcher
echo =======================================================================
echo.

:: 1. Überprüfe ob Java installiert ist und im PATH liegt
echo [1/3] Überprüfe Java-Installation...
java -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    color 0C
    echo.
    echo [FEHLER] Java wurde nicht in den Umgebungsvariablen gefunden.
    echo Bitte stellen Sie sicher, dass Java (Version 11 oder neuer) installiert
    echo und in der Systemvariable PATH eingetragen ist.
    goto error_end
)

:: 2. Überprüfe ob Maven installiert ist und im PATH liegt
echo [2/3] Überprüfe Maven-Installation...
mvn -version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    color 0C
    echo.
    echo [FEHLER] Maven (mvn) wurde nicht in den Umgebungsvariablen gefunden.
    echo Bitte installieren Sie Apache Maven und fügen Sie es zu PATH hinzu.
    goto error_end
)

:: 3. Starte die Anwendung
echo [3/3] Starte Anwendung über Maven...
echo.
echo Ausführen: mvn javafx:run
call mvn javafx:run

:: Falls javafx:run fehlschlägt, versuche exec:java als Fallback
if %ERRORLEVEL% neq 0 (
    echo.
    echo [WARNUNG] Start über javafx:run fehlgeschlagen (Fehlercode %ERRORLEVEL%).
    echo Versuche alternativen Start über exec:java...
    echo.
    call mvn compile exec:java -Dexec.mainClass="SignalProviderTable"
)

:: Abschluss
if %ERRORLEVEL% neq 0 (
    color 0C
    echo.
    echo [FEHLER] Die Anwendung konnte nicht gestartet werden.
    goto error_end
)

echo.
echo =======================================================================
echo Anwendung wurde erfolgreich beendet.
echo =======================================================================
pause
exit /b 0

:error_end
echo.
echo =======================================================================
echo Startvorgang abgebrochen. Bitte überprüfen Sie die obigen Fehlermeldungen.
echo =======================================================================
pause
exit /b 1
