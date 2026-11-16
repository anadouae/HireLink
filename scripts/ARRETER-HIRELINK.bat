@echo off
chcp 65001 >nul
setlocal EnableExtensions
title HireLink — Arrêt ciblé

:: N'arrête QUE les processus qui écoutent les ports configurés
:: ET dont la ligne de commande contient "hirelink" (évite de tuer un inconnu).
if "%HIRELINK_PORT%"=="" set "HIRELINK_PORT=8081"
if "%HIRELINK_FE_PORT%"=="" set "HIRELINK_FE_PORT=5500"

echo ============================================================
echo   HireLink — Arret cible ports %HIRELINK_PORT% / %HIRELINK_FE_PORT%
echo   Regle : ligne de commande doit contenir "hirelink" ou "serve"
echo ============================================================
echo.

powershell -NoProfile -Command ^
  "$ports = @(%HIRELINK_PORT%, %HIRELINK_FE_PORT%);" ^
  "foreach ($p in $ports) {" ^
  "  $conns = Get-NetTCPConnection -LocalPort $p -State Listen -ErrorAction SilentlyContinue;" ^
  "  foreach ($c in $conns) {" ^
  "    $proc = Get-CimInstance Win32_Process -Filter (\"ProcessId=$($c.OwningProcess)\") -ErrorAction SilentlyContinue;" ^
  "    if (-not $proc) { Write-Host \"Port $p : PID $($c.OwningProcess) introuvable\"; continue }" ^
  "    $cmd = [string]$proc.CommandLine;" ^
  "    if ($cmd -match 'hirelink|serve\\.js|\\\\serve\\\\' -or $cmd -match 'npx.*serve') {" ^
  "      Write-Host \"Arret PID $($proc.ProcessId) sur port $p\";" ^
  "      Stop-Process -Id $proc.ProcessId -Force -ErrorAction SilentlyContinue" ^
  "    } else {" ^
  "      Write-Host \"REFUS : port $p PID $($proc.ProcessId) — ligne de commande hors HireLink. Non touche.\"" ^
  "    }" ^
  "  }" ^
  "}"

echo Termine.
exit /b 0
