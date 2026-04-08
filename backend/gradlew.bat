@echo off
setlocal

set DIRNAME=%~dp0
set APP_HOME=%DIRNAME%

set DEFAULT_JVM_OPTS=-Xmx4096m -Dfile.encoding=UTF-8

where java >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo ERROR: java not found in PATH
    exit /b 1
)

set CLASSPATH=%APP_HOME%\gradle\wrapper\gradle-wrapper.jar

java %DEFAULT_JVM_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
