@echo off
chcp 65001 >nul
setlocal EnableExtensions
title HireLink Backend — TEST

for %%I in ("%~dp0..") do set "REPO_ROOT=%%~fI"
set "BACKEND_DIR=%REPO_ROOT%\backend"
set "MYSQL_CLIENT=C:\xampp\mysql\bin\mysql.exe"
set "HIRELINK_PORT=8081"

echo ============================================================
echo   HireLink — Backend de test
echo   Base imposee : hirelink_test
echo   Hibernate : validate
echo   Seed / synchronisations : desactives
echo   SMTP : neutralise (127.0.0.1:1, aucun envoi externe)
echo ============================================================
echo.

where java >nul 2>nul || (echo ERREUR : Java introuvable. & exit /b 1)
where mvn >nul 2>nul || (echo ERREUR : Maven introuvable. & exit /b 1)
if not exist "%MYSQL_CLIENT%" (
  echo ERREUR : client MySQL introuvable : %MYSQL_CLIENT%
  exit /b 1
)

"%MYSQL_CLIENT%" -u root --batch --skip-column-names -e "SELECT SCHEMA_NAME FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='hirelink_test';" 2>nul | findstr /x "hirelink_test" >nul
if errorlevel 1 (
  echo ERREUR : hirelink_test est absente ou inaccessible. Aucune base ne sera creee.
  exit /b 1
)

powershell -NoProfile -Command "try { $c=New-Object Net.Sockets.TcpClient; $c.ReceiveTimeout=800; $c.Connect('127.0.0.1',%HIRELINK_PORT%); $c.Close(); exit 0 } catch { exit 1 }"
if not errorlevel 1 (
  echo ERREUR : le port %HIRELINK_PORT% repond deja.
  echo Aucun processus n'a ete arrete.
  exit /b 2
)

set "SPRING_PROFILES_ACTIVE=default"
set "SPRING_APPLICATION_JSON={"spring.datasource.url":"jdbc:mysql://127.0.0.1:3306/hirelink_test?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=8000","spring.datasource.username":"root","spring.datasource.password":"","spring.jpa.hibernate.ddl-auto":"validate","hirelink.seed.enabled":false,"hirelink.seed.force-reset":false,"hirelink.startup-migrations.enabled":false,"hirelink.external-jobs.sync-enabled":false,"hirelink.external-jobs.startup-sync":false,"hirelink.external-jobs.ats.scheduled-sync":false,"hirelink.external-jobs.maroc.enabled":false,"hirelink.external-jobs.remoteok.enabled":false,"hirelink.external-jobs.greenhouse.enabled":false,"hirelink.external-jobs.lever.enabled":false,"hirelink.external-jobs.ashby.enabled":false,"spring.mail.host":"127.0.0.1","spring.mail.port":1,"spring.mail.username":"","spring.mail.password":"","spring.mail.properties.mail.smtp.auth":false,"spring.mail.properties.mail.smtp.connectiontimeout":500,"spring.mail.properties.mail.smtp.timeout":500,"spring.mail.properties.mail.smtp.writetimeout":500}"

echo Demarrage du backend dans une nouvelle fenetre...
start "HireLink Backend - TEST" /D "%BACKEND_DIR%" cmd /k "mvn.cmd -B spring-boot:run"

echo Attente de la confirmation HTTP et de la connexion MariaDB...
powershell -NoProfile -Command "$mysql='%MYSQL_CLIENT%'; for ($i=0; $i -lt 60; $i++) { $health=$null; try { $health=Invoke-RestMethod -Uri 'http://127.0.0.1:%HIRELINK_PORT%/api/v1/health' -TimeoutSec 2 } catch {}; $db=& $mysql -u root --batch --skip-column-names -e 'SELECT COUNT(*) FROM information_schema.PROCESSLIST WHERE DB = ''hirelink_test''' 2>$null; if ($health.status -eq 'UP' -and [int]$db -gt 0) { Write-Output 'Base connectee : hirelink_test'; exit 0 }; Start-Sleep -Seconds 1 }; Write-Output 'ERREUR : connexion non confirmee; verifiez la fenetre du backend.'; exit 1"
if errorlevel 1 exit /b 1

echo Backend de test pret sur http://127.0.0.1:%HIRELINK_PORT%
exit /b 0
