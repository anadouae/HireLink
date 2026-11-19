@echo off
chcp 65001 >nul
setlocal EnableExtensions
title HireLink — Backend

:: Racine du dépôt = parent de scripts\
cd /d "%~dp0.."

if "%HIRELINK_PORT%"=="" set "HIRELINK_PORT=8081"
if "%HIRELINK_FE_ORIGIN%"=="" set "HIRELINK_FE_ORIGIN=http://127.0.0.1:5500"

echo ============================================================
echo   HireLink — Backend
echo   Port : %HIRELINK_PORT%
echo   Frontend attendu : %HIRELINK_FE_ORIGIN%
echo   Base par defaut : hirelink_test (ddl-auto=validate)
echo ============================================================
echo.

where java >nul 2>nul || (echo ERREUR : Java introuvable. & exit /b 1)
where mvn >nul 2>nul || (echo ERREUR : Maven introuvable. & exit /b 1)

powershell -NoProfile -Command "try { $c=New-Object Net.Sockets.TcpClient; $c.ReceiveTimeout=800; $c.Connect('127.0.0.1',%HIRELINK_PORT%); $c.Close(); exit 0 } catch { exit 1 }"
if not errorlevel 1 (
  echo ERREUR : le port %HIRELINK_PORT% repond deja.
  echo Aucun processus n'a ete arrete.
  exit /b 2
)

if not exist "backend\application-local.properties" (
  if exist "backend\src\main\resources\application-local.properties.example" (
    echo INFO : application-local.properties absent.
    echo        Copiez backend\src\main\resources\application-local.properties.example
    echo        vers backend\application-local.properties et renseignez JDBC.
  )
)

cd backend
echo Demarrage Spring Boot ^(SMTP coupe, OTP console off par defaut^)...
call mvn.cmd -B spring-boot:run -Dspring-boot.run.jvmArguments="-Dserver.port=%HIRELINK_PORT% -Dspring.mail.host= -Dhirelink.otp.log-to-console=false -Dhirelink.cors.allowed-origins=%HIRELINK_FE_ORIGIN%,http://localhost:5500,http://127.0.0.1:5500 -Dhirelink.frontend.base-url=%HIRELINK_FE_ORIGIN%"
exit /b %ERRORLEVEL%
