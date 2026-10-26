@echo off
setlocal
set "ROOT=%~dp0"
set "BUILD_DIR=%ROOT%.build"
if not exist "%BUILD_DIR%" mkdir "%BUILD_DIR%"
pushd "%ROOT%"
javac -encoding UTF-8 -d "%BUILD_DIR%" GoblinDoorsGUI.java GameAudio.java GameAssets.java
if errorlevel 1 (
    echo.
    echo Build failed. Install a Java JDK and try again.
    popd
    pause
    exit /b 1
)
java -cp "%BUILD_DIR%" GoblinDoorsGUI
set "RESULT=%ERRORLEVEL%"
popd
exit /b %RESULT%
