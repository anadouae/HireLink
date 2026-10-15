@echo off
title HireLink ? start stack
echo [1/4] MySQL...
if not exist "C:\xampp\mysql_start.bat" goto skipmysql
start "" /MIN "C:\xampp\mysql_start.bat"
:skipmysql
echo [2/4] Apache / phpMyAdmin...
if not exist "C:\xampp\apache_start.bat" goto skipapache
start "" /MIN "C:\xampp\apache_start.bat"
:skipapache
timeout /t 3 /nobreak >nul
echo [3/4] Backend :8081...
set JAVA_HOME=C:\Program Files\Java\jdk-23
start "HireLink API" cmd /k "cd /d C:\Users\daurinia\Desktop\HireLink\backend && C:\Users\daurinia\tools\apache-maven-3.9.16\bin\mvn.cmd spring-boot:run -DskipTests"
echo [4/4] Frontend :5500...
start "HireLink FE" cmd /k "cd /d C:\Users\daurinia\Desktop\HireLink\frontend && python -m http.server 5500 --bind 127.0.0.1"
timeout /t 8 /nobreak >nul
start http://127.0.0.1:5500/index.html
start http://127.0.0.1:5500/auth.html?mode=login
start http://127.0.0.1/phpmyadmin/
echo.
echo Stack lancee. Comptes:
echo   candidat@hirelink.ma / Candidat123!
echo   n.bennani@atlasdigital.ma / Recruteur123!
echo   admin@hirelink.ma / Admin123!
pause
