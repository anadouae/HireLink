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

cd /d "%~dp0backend"
call mvn.cmd -B spring-boot:run -Dspring-boot.run.jvmArguments="-Dspring.mail.host= -Dhirelink.otp.log-to-console=false"
