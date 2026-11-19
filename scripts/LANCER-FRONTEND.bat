@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
title HireLink — Frontend

cd /d "%~dp0..\frontend"
if not exist "index.html" (
  echo ERREUR : dossier frontend introuvable à côté du dossier scripts.
  pause
  exit /b 1
)
if "%HIRELINK_FE_PORT%"=="" set "HIRELINK_FE_PORT=5500"

echo ============================================================
echo   HireLink — Frontend
echo   Adresse : http://127.0.0.1:%HIRELINK_FE_PORT%
echo   Arrêt : Ctrl+C dans cette fenêtre, ou scripts\ARRETER-HIRELINK.bat
echo ============================================================
echo.

:: Port déjà occupé ? On ne touche à rien et on prévient.
set "BUSY=0"
for /f "tokens=1,2,3" %%A in ('netstat -ano') do (
  if /I "%%A"=="TCP" (
    set "LOCAL=%%B#"
    if not "!LOCAL::%HIRELINK_FE_PORT%#=!"=="!LOCAL!" (
      if "%%C"=="0.0.0.0:0" set "BUSY=1"
      if "%%C"=="[::]:0" set "BUSY=1"
    )
  )
)
if "!BUSY!"=="1" (
  echo Le port %HIRELINK_FE_PORT% est déjà utilisé : le frontend tourne peut-être déjà.
  echo Ouvrez http://127.0.0.1:%HIRELINK_FE_PORT% ou lancez d'abord scripts\ARRETER-HIRELINK.bat.
  pause
  exit /b 2
)

where py >nul 2>nul
if not errorlevel 1 (
  py -m http.server %HIRELINK_FE_PORT%
  exit /b !ERRORLEVEL!
)
where python >nul 2>nul
if not errorlevel 1 (
  python -m http.server %HIRELINK_FE_PORT%
  exit /b !ERRORLEVEL!
)
echo ERREUR : Python est introuvable. Installez Python 3 depuis python.org
echo ^(cochez « Add python.exe to PATH »^), puis relancez ce fichier.
pause
exit /b 1
