@echo off
chcp 65001 >nul
title HireLink — Backend Spring Boot

echo ============================================================
echo   HireLink — Backend (Spring Boot + MySQL XAMPP)
echo   API : http://localhost:8081/api/v1
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

echo Verification Java...
java -version 2>&1
if errorlevel 1 (
    echo ERREUR : Java 21 ou plus introuvable.
    echo Installez un JDK 21, cochez "Add to PATH", puis rouvrez cette fenetre.
    pause
    exit /b 1
)
where mvn >nul 2>nul
if errorlevel 1 (
    echo ERREUR : Maven introuvable.
    echo Installez Maven 3.9 et ajoutez son dossier bin au PATH.
    pause
    exit /b 1
)

call :port_occupe %HIRELINK_PORT%
if not errorlevel 1 (
    echo ERREUR : le port %HIRELINK_PORT% repond deja.
    echo Le script s'arrete sans toucher au processus en cours.
    if /I not "%HIRELINK_CHECK_ONLY%"=="1" pause
    exit /b 2
)

if /I "%HIRELINK_CHECK_ONLY%"=="1" (
    echo Verification terminee : Java, Maven et le port %HIRELINK_PORT% sont prets. Aucun service n'est demarre.
    exit /b 0
)

echo.
echo IMPORTANT : XAMPP doit etre demarre (MySQL sur port 3306).
echo Si MySQL n'est pas lance, le backend echouera au demarrage.
echo.
echo Appuyez sur une touche pour lancer le backend...
pause >nul

cd /d "%~dp0backend"

echo.
echo === Demarrage en cours (peut prendre 20-30 secondes) ===
echo Ctrl+C pour arreter le backend.
echo.

mvn spring-boot:run
exit /b %ERRORLEVEL%

:port_occupe
powershell -NoProfile -Command "try { $c = New-Object System.Net.Sockets.TcpClient; $c.ReceiveTimeout = 1500; $c.SendTimeout = 1500; $c.Connect('127.0.0.1', %1); $c.Close(); exit 0 } catch { exit 1 }"
if errorlevel 1 exit /b 1
exit /b 0
