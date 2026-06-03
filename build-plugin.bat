@echo off
setlocal enabledelayedexpansion

rem 自动查找 JDK 17+，避免 Gradle 误用系统默认 Java 8
set "JAVA17="

for /d %%i in ("C:\Program Files\Eclipse Adoptium\jdk-17*") do set "JAVA17=%%i"
if not defined JAVA17 for /d %%i in ("C:\Program Files\Java\jdk-17*") do set "JAVA17=%%i"
if not defined JAVA17 for /d %%i in ("C:\Program Files\Microsoft\jdk-17*") do set "JAVA17=%%i"
if not defined JAVA17 for /d %%i in ("C:\Program Files\JetBrains\IntelliJ IDEA*\jbr") do set "JAVA17=%%i"

if not defined JAVA17 (
    echo [错误] 未找到 JDK 17，请先安装：
    echo   winget install EclipseAdoptium.Temurin.17.JDK
    echo 或从 https://adoptium.net/temurin/releases/?version=17 下载安装
    exit /b 1
)

echo 使用 JDK: %JAVA17%
set "JAVA_HOME=%JAVA17%"
set "PATH=%JAVA17%\bin;%PATH%"

java -version
echo.
call gradlew.bat buildPlugin %*
