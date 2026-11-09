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

where python >nul 2>nul
if errorlevel 1 (
    echo Python non trouve. Ouverture directe dans le navigateur...
    start "" "%~dp0frontend\index.html"
    echo.
    echo Pour un vrai serveur HTTP, installez Python ou utilisez
    echo l'extension "Live Server" dans VS Code.
    pause
    exit /b
)

echo Demarrage du serveur HTTP sur http://localhost:5500
echo Ctrl+C pour arreter.
echo.
cd /d "%~dp0frontend"
python -m http.server 5500

pause

