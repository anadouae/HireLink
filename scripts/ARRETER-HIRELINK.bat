@echo off
chcp 65001 >nul
setlocal EnableExtensions EnableDelayedExpansion
title HireLink — Arrêt

:: Arrête uniquement les processus qui écoutent sur les ports HireLink
:: (backend 8081 et frontend 5500 par défaut). Les PID sont lus avec netstat :
:: aucun arrêt par titre de fenêtre, aucun autre processus touché.
if "%HIRELINK_PORT%"=="" set "HIRELINK_PORT=8081"
if "%HIRELINK_FE_PORT%"=="" set "HIRELINK_FE_PORT=5500"

echo ============================================================
echo   HireLink — Arrêt des ports %HIRELINK_PORT% et %HIRELINK_FE_PORT%
echo ============================================================
echo.

set "DONE= "
set "COUNT=0"
for %%P in (%HIRELINK_PORT% %HIRELINK_FE_PORT%) do (
  set "HIT=0"
  for /f "tokens=1,2,3,5" %%A in ('netstat -ano') do (
    if /I "%%A"=="TCP" (
      set "LOCAL=%%B#"
      set "REMOTE=%%C"
      set "PID=%%D"
      if not "!LOCAL::%%P#=!"=="!LOCAL!" (
        set "LISTEN=0"
        if "!REMOTE!"=="0.0.0.0:0" set "LISTEN=1"
        if "!REMOTE!"=="[::]:0" set "LISTEN=1"
        if "!LISTEN!"=="1" if not "!PID!"=="0" (
          set "HIT=1"
          if "!DONE: %%D =!"=="!DONE!" (
            set "DONE=!DONE!%%D "
            set "NAME=?"
            for /f "tokens=1 delims=," %%N in ('tasklist /FI "PID eq %%D" /FO CSV /NH 2^>nul') do set "NAME=%%~N"
            echo Port %%P : arrêt du PID %%D ^(!NAME!^)
            taskkill /PID %%D /F >nul 2>nul
            if errorlevel 1 (
              echo   Impossible d'arrêter le PID %%D ^(droits insuffisants ?^)
            ) else (
              set /a COUNT+=1
            )
          )
        )
      )
    )
  )
  if "!HIT!"=="0" echo Port %%P : aucun processus à l'écoute.
)

echo.
echo Terminé : !COUNT! processus arrêté^(s^).
exit /b 0
