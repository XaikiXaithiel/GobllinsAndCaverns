@echo off
setlocal
set "ROOT=%~dp0"
set "BUILD_DIR=%ROOT%.build"
set "DEPS_DIR=%ROOT%.deps"
set "JNA_JAR=%DEPS_DIR%\jna-5.14.0.jar"
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
if not exist "%DEPS_DIR%" mkdir "%DEPS_DIR%"
pushd "%ROOT%"
if not exist "%JNA_JAR%" (
    echo Downloading JNA for XInput controller support...
    curl.exe -fL --retry 3 -o "%JNA_JAR%" "https://repo.maven.apache.org/maven2/net/java/dev/jna/jna/5.14.0/jna-5.14.0.jar"
    if errorlevel 1 (
        if exist "%JNA_JAR%" del /q "%JNA_JAR%"
        echo.
        echo Could not download JNA. Check your internet connection and try again.
        popd
        exit /b 1
    )
)
javac -encoding UTF-8 -cp "%JNA_JAR%" -d "%BUILD_DIR%" GoblinDoorsGUI.java GameAudio.java GameAssets.java XInputController.java
if errorlevel 1 (
    echo.
    echo Build failed. Install a Java JDK and try again.
    popd
    pause
    exit /b 1
)
java -cp "%BUILD_DIR%;%JNA_JAR%" GoblinDoorsGUI
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%
