@echo off
chcp 65001 >nul
title HireLink — Backend demonstration

echo ============================================================
echo   HireLink — Backend de demonstration
echo   API : http://localhost:8081/api/v1
echo   SMTP coupe. Les codes a usage unique ne s'affichent pas.
echo   Base et cle JWT : celles du fichier application.properties.
echo ============================================================
echo.

:: Java 21 ou plus, puis Maven : d'abord le PATH, ensuite les installations habituelles.
where java >nul 2>nul
if errorlevel 1 (
    if exist "C:\Program Files\Java\jdk-23\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-23"
    if not defined JAVA_HOME if exist "C:\Program Files\Java\jdk-21\bin\java.exe" set "JAVA_HOME=C:\Program Files\Java\jdk-21"
    if not defined JAVA_HOME if exist "C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot\bin\java.exe" set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.5.11-hotspot"
    if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
)
where mvn >nul 2>nul
if errorlevel 1 (
    if exist "C:\apache-maven-3.10.0\bin\mvn.cmd" set "PATH=C:\apache-maven-3.10.0\bin;%PATH%"
    if exist "%USERPROFILE%\apache-maven\bin\mvn.cmd" set "PATH=%USERPROFILE%\apache-maven\bin;%PATH%"
)

if "%HIRELINK_PORT%"=="" set "HIRELINK_PORT=8081"

java -version >nul 2>&1
if errorlevel 1 (
    echo ERREUR : Java 21 ou plus introuvable.
    pause
    exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
    echo ERREUR : Maven introuvable.
    pause
    exit /b 1
)

call :port_occupe %HIRELINK_PORT%
if not errorlevel 1 (
    echo ERREUR : le port %HIRELINK_PORT% repond deja.
    echo Aucun processus n'a ete arrete et aucune configuration n'a ete modifiee.
    if /I not "%HIRELINK_CHECK_ONLY%"=="1" pause
    exit /b 2
)

if /I "%HIRELINK_CHECK_ONLY%"=="1" (
    echo Verification terminee : Java, Maven et le port %HIRELINK_PORT% sont prets. Aucun service n'est demarre.
    exit /b 0
)

cd /d "%~dp0backend"
call mvn.cmd -B spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.mail.host= -Dhirelink.otp.log-to-console=false"
exit /b %ERRORLEVEL%

:port_occupe
powershell -NoProfile -Command "try { $c = New-Object System.Net.Sockets.TcpClient; $c.ReceiveTimeout = 1500; $c.SendTimeout = 1500; $c.Connect('127.0.0.1', %1); $c.Close(); exit 0 } catch { exit 1 }"
if errorlevel 1 exit /b 1
exit /b 0
