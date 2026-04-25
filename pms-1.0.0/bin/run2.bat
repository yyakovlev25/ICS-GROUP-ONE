@echo off
REM setlocal EnableExtensions EnableDelayedExpansion

REM ------------------------------------------------------------
REM Java command resolution
REM Priority:
REM   1) JAVA_CMD (explicit full path to java.exe)
REM      set JAVA_CMD="C:\path\to\java"
REM   2) JAVA_HOME\bin\java.exe
REM      set JAVA_HOME="C:\path\to\java\home"
REM   4) java from PATH as fallback
REM ------------------------------------------------------------

REM ------------------------------------------------------------
set "APP_DIR=%~dp0.."
for %%I in ("%APP_DIR%") do set "APP_DIR=%%~fI"

set "APP_JAR=%APP_DIR%\pms-1.0.0.jar"
set "CP=%APP_JAR%;%APP_DIR%\lib\*"

REM Optional Java options (can be set outside, e.g. set JAVA_OPTS=-Xmx512m)
if not defined JAVA_OPTS set "JAVA_OPTS="

REM Identfiy java binary...
set "JAVA_EXE="

if defined JAVA_CMD (
  set "JAVA_EXE=%JAVA_CMD%"
)

if not defined JAVA_EXE (
  if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" (
      set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
    )
  )
)

if not defined JAVA_EXE (
  set "JAVA_EXE=java"
)


REM Optional: show resolved java version
"%JAVA_EXE%" -version

"%JAVA_EXE%" %JAVA_OPTS% -cp "%CP%" de.mbg.pms.ServerMain2 %*
endlocal