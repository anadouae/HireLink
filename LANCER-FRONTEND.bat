@echo off
chcp 65001 >nul
title HireLink — Frontend (Python HTTP Server)

echo ============================================================
echo   HireLink — Lancement du Frontend
echo   Serveur sur http://localhost:5500
echo ============================================================
echo.

where python >nul 2>nul
if errorlevel 1 (
    echo Python non trouve. Ouverture directe dans le navigateur...
    start "" "C:\Users\daurinia\Desktop\HireLink\frontend\index.html"
    echo.
    echo Pour un vrai serveur HTTP, installez Python ou utilisez
    echo l'extension "Live Server" dans VS Code.
    pause
    exit /b
)

echo Demarrage du serveur HTTP sur http://localhost:5500
echo Ctrl+C pour arreter.
echo.
cd /d "C:\Users\daurinia\Desktop\HireLink\frontend"
python -m http.server 5500

pause

