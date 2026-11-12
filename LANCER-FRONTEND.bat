@echo off
chcp 65001 >nul
title HireLink — Frontend (Python HTTP Server)

echo ============================================================
echo   HireLink — Lancement du Frontend
echo   Serveur sur http://localhost:5500
echo ============================================================
echo.

rem Tous les chemins sont relatifs au dossier de ce script : le depot est portable.
rem Si un autre serveur HTTP sert deja frontend/ (Live Server, etc.), ce script n'est pas obligatoire.
if not exist "%~dp0frontend\index.html" (
    echo ERREUR : frontend\index.html introuvable a cote de ce script.
    echo Placez ce fichier .bat a la racine du depot HireLink.
    pause
    exit /b 1
)

if "%HIRELINK_PORT%"=="" set "HIRELINK_PORT=5500"

where python >nul 2>nul
if errorlevel 1 (
    echo Python non trouve. Ouverture directe dans le navigateur...
    if /I "%HIRELINK_CHECK_ONLY%"=="1" exit /b 1
    start "" "%~dp0frontend\index.html"
    echo.
    echo Pour un vrai serveur HTTP, installez Python ou utilisez
    echo l'extension "Live Server" dans VS Code.
    pause
    exit /b 1
)

call :port_occupe %HIRELINK_PORT%
if not errorlevel 1 (
    echo ERREUR : le port %HIRELINK_PORT% repond deja.
    echo Aucun second serveur n'est demarre.
    exit /b 2
)

if /I "%HIRELINK_CHECK_ONLY%"=="1" (
    echo Verification terminee : Python et le port %HIRELINK_PORT% sont prets. Aucun service n'est demarre.
    exit /b 0
)

echo Demarrage du serveur HTTP sur http://localhost:5500
echo Ctrl+C pour arreter.
echo.
cd /d "%~dp0frontend"
python -m http.server 5500
exit /b %ERRORLEVEL%

:port_occupe
powershell -NoProfile -Command "try { $c = New-Object System.Net.Sockets.TcpClient; $c.ReceiveTimeout = 1500; $c.SendTimeout = 1500; $c.Connect('127.0.0.1', %1); $c.Close(); exit 0 } catch { exit 1 }"
if errorlevel 1 exit /b 1
exit /b 0

