@echo off
chcp 65001 >nul
title HireLink — Backend Spring Boot

echo ============================================================
echo   HireLink — Backend (Spring Boot + MySQL XAMPP)
echo   API : http://localhost:8081/api/v1
echo ============================================================
echo.

:: Java 23 (compatible avec pom.xml qui exige Java 21+)
set JAVA_HOME=C:\Program Files\Java\jdk-23
set PATH=%JAVA_HOME%\bin;C:\apache-maven-3.10.0\bin;%PATH%

echo Verification Java...
java -version 2>&1
if errorlevel 1 (
    echo ERREUR : Java 23 introuvable dans C:\Program Files\Java\jdk-23
    echo Verifiez le chemin ou installez Java 21+.
    pause
    exit /b 1
)

echo.
echo IMPORTANT : XAMPP doit etre demarre (MySQL sur port 3306).
echo Si MySQL n'est pas lance, le backend echouera au demarrage.
echo.
echo Appuyez sur une touche pour lancer le backend...
pause >nul

cd /d "C:\Users\daurinia\Desktop\HireLink\backend"

echo.
echo === Demarrage en cours (peut prendre 20-30 secondes) ===
echo Ctrl+C pour arreter le backend.
echo.

mvn spring-boot:run

pause
