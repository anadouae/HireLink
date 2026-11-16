@echo off
chcp 65001 >nul
setlocal EnableExtensions
title HireLink — Frontend

cd /d "%~dp0.."
if "%HIRELINK_FE_PORT%"=="" set "HIRELINK_FE_PORT=5500"

echo ============================================================
echo   HireLink — Frontend statique
echo   URL : http://127.0.0.1:%HIRELINK_FE_PORT%
echo ============================================================
echo.

powershell -NoProfile -Command "try { $c=New-Object Net.Sockets.TcpClient; $c.ReceiveTimeout=800; $c.Connect('127.0.0.1',%HIRELINK_FE_PORT%); $c.Close(); exit 0 } catch { exit 1 }"
if not errorlevel 1 (
  echo ERREUR : le port %HIRELINK_FE_PORT% repond deja. Aucun processus tue.
  exit /b 2
)

where node >nul 2>nul
if errorlevel 1 (
  echo ERREUR : Node.js requis pour "npx serve".
  echo Alternative : ouvrir frontend\ avec un serveur HTTP local de votre choix.
  exit /b 1
)

cd frontend
echo Lancement serve ^(cleanUrls=false via serve.json si present^)...
npx --yes serve -l %HIRELINK_FE_PORT% -n .
exit /b %ERRORLEVEL%
