@echo off
chcp 65001 >nul
setlocal EnableExtensions
title HireLink — Création base de démonstration

:: Paramètres configurables (aucun chemin personnel obligatoire)
if "%HIRELINK_DB_HOST%"=="" set "HIRELINK_DB_HOST=127.0.0.1"
if "%HIRELINK_DB_PORT%"=="" set "HIRELINK_DB_PORT=3306"
if "%HIRELINK_DB_NAME%"=="" set "HIRELINK_DB_NAME=hirelink_demo"
if "%HIRELINK_DB_USER%"=="" set "HIRELINK_DB_USER=root"
if "%HIRELINK_DB_PASSWORD%"=="" set "HIRELINK_DB_PASSWORD="

echo ============================================================
echo   HireLink — Creation de base ^(si absente^)
echo   Hote : %HIRELINK_DB_HOST%:%HIRELINK_DB_PORT%
echo   Base : %HIRELINK_DB_NAME%
echo   Utilisateur : %HIRELINK_DB_USER%
echo ============================================================
echo.
echo Cette commande NE SUPPRIME JAMAIS une base existante.
echo.

set "MYSQL_BIN="
where mysql >nul 2>nul && set "MYSQL_BIN=mysql"
if "%MYSQL_BIN%"=="" if exist "%ProgramFiles%\MySQL\MySQL Server 8.0\bin\mysql.exe" set "MYSQL_BIN=%ProgramFiles%\MySQL\MySQL Server 8.0\bin\mysql.exe"
if "%MYSQL_BIN%"=="" if exist "%ProgramFiles%\MySQL\MySQL Server 9.2\bin\mysql.exe" set "MYSQL_BIN=%ProgramFiles%\MySQL\MySQL Server 9.2\bin\mysql.exe"
if "%MYSQL_BIN%"=="" if exist "C:\xampp\mysql\bin\mysql.exe" set "MYSQL_BIN=C:\xampp\mysql\bin\mysql.exe"
:: Prefer XAMPP/MariaDB si present (souvent le serveur reel sur :3306 sous Windows)
if exist "C:\xampp\mysql\bin\mysql.exe" set "MYSQL_BIN=C:\xampp\mysql\bin\mysql.exe"
if "%MYSQL_BIN%"=="" (
  echo ERREUR : mysql.exe introuvable. Ajoutez le client MySQL au PATH
  echo         ou definissez MYSQL_BIN vers mysql.exe.
  exit /b 1
)
echo Client : %MYSQL_BIN%


set "MYSQL_PWD=%HIRELINK_DB_PASSWORD%"
"%MYSQL_BIN%" -h %HIRELINK_DB_HOST% -P %HIRELINK_DB_PORT% -u %HIRELINK_DB_USER% -N -e "SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA WHERE SCHEMA_NAME='%HIRELINK_DB_NAME%';" > "%TEMP%\hl_db_check.txt" 2>nul
set "MYSQL_PWD="

findstr /i /x "%HIRELINK_DB_NAME%" "%TEMP%\hl_db_check.txt" >nul 2>nul
if not errorlevel 1 (
  echo REFUS : la base "%HIRELINK_DB_NAME%" existe deja. Aucune modification.
  echo Choisissez un autre HIRELINK_DB_NAME ou utilisez la base existante.
  del "%TEMP%\hl_db_check.txt" >nul 2>nul
  exit /b 2
)

set "MYSQL_PWD=%HIRELINK_DB_PASSWORD%"
"%MYSQL_BIN%" -h %HIRELINK_DB_HOST% -P %HIRELINK_DB_PORT% -u %HIRELINK_DB_USER% -e "CREATE DATABASE `%HIRELINK_DB_NAME%` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
set "RC=%ERRORLEVEL%"
set "MYSQL_PWD="
del "%TEMP%\hl_db_check.txt" >nul 2>nul

if not "%RC%"=="0" (
  echo ERREUR : creation refusee ^(code %RC%^).
  exit /b %RC%
)

echo OK : base "%HIRELINK_DB_NAME%" creee.
echo Prochaine etape : copier application-local.properties.example puis LANCER-BACKEND.bat
exit /b 0
