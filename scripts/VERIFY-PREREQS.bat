@echo off
chcp 65001 >nul
setlocal EnableExtensions
title HireLink — Vérification des prérequis

echo ============================================================
echo   HireLink — Prérequis (aucune installation, aucun démarrage)
echo ============================================================
echo.

set "ERR=0"

where java >nul 2>nul
if errorlevel 1 (
  echo [MANQUE] Java ^(21+^) introuvable dans le PATH.
  set "ERR=1"
) else (
  for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do echo [OK] Java %%~v
)

where mvn >nul 2>nul
if errorlevel 1 (
  echo [MANQUE] Maven introuvable dans le PATH.
  set "ERR=1"
) else (
  echo [OK] Maven present
)

where mysql >nul 2>nul
if errorlevel 1 (
  echo [INFO] mysql.exe absent du PATH — acceptable si le serveur MySQL tourne et que vous utilisez Workbench.
) else (
  echo [OK] Client mysql present
)

where py >nul 2>nul
if errorlevel 1 (
  where python >nul 2>nul
  if errorlevel 1 (
    echo [ERREUR] Python absent — requis pour servir le frontend ^(py -m http.server 5500^).
  ) else (
    echo [OK] Python present
  )
) else (
  echo [OK] Python present ^(py^)
)

where node >nul 2>nul
if errorlevel 1 (
  echo [INFO] Node.js absent — requis seulement pour les tests Playwright.
) else (
  echo [OK] Node.js present
)

echo.
echo Ports par defaut ^(verification passive, aucun processus tue^) :
call :check_port 8081 "Backend demo / prof"
call :check_port 5500 "Frontend demo / prof"
call :check_port 18081 "Backend ISO ^(ne pas utiliser pour la livraison prof^)"
call :check_port 15500 "Frontend ISO"

echo.
if "%ERR%"=="1" (
  echo RESULTAT : des prequis manquent. Corrigez puis relancez.
  exit /b 1
)
echo RESULTAT : prequis de base OK.
exit /b 0

:check_port
powershell -NoProfile -Command "try { $c=New-Object Net.Sockets.TcpClient; $c.ReceiveTimeout=800; $c.Connect('127.0.0.1',%1); $c.Close(); Write-Host '[OCCUPE] port %1 — %~2' } catch { Write-Host '[LIBRE ] port %1 — %~2' }"
exit /b 0
