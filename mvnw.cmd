@echo off
REM Maven Wrapper for Windows
REM This script requires Maven to be installed on your system

set MAVEN_PROJECT_DIR=%~dp0
set MAVEN_OPTS=-Xmx1024m

if exist "%MAVEN_PROJECT_DIR%\.mvn\wrapper\maven-wrapper.jar" (
    echo Using Maven Wrapper...
    java -jar "%MAVEN_PROJECT_DIR%\.mvn\wrapper\maven-wrapper.jar" %*
) else (
    echo Maven Wrapper JAR not found.
    echo Please install Maven or download the wrapper JAR.
    echo.
    echo You can install Maven from: https://maven.apache.org/download.cgi
    echo.
    echo Or run this command to generate the wrapper:
    echo mvn wrapper:wrapper
    echo.
    exit /b 1
)
