@echo off
setlocal EnableDelayedExpansion

call "%~dp0gradlew.bat" clean assembleRelease
if errorlevel 1 exit /b %errorlevel%

set "RELEASE_DIR=%~dp0app\build\outputs\apk\release"
set "WEBSITE_ASSETS_DIR=%~dp0website\assets"
set "COPIED=0"

if not exist "%WEBSITE_ASSETS_DIR%" (
    mkdir "%WEBSITE_ASSETS_DIR%"
    if errorlevel 1 exit /b %errorlevel%
)

del /q "%WEBSITE_ASSETS_DIR%\DCTimer-BLE-v*.apk" 2>nul

echo.
for %%F in ("%RELEASE_DIR%\*.apk") do (
    if exist "%%~fF" (
        copy /y "%%~fF" "%WEBSITE_ASSETS_DIR%\%%~nxF" >nul
        if errorlevel 1 exit /b %errorlevel%
        set /a COPIED+=1
        echo Release APK:
        echo %%~fF
        echo Website APK:
        echo %WEBSITE_ASSETS_DIR%\%%~nxF
        echo.
    )
)

if !COPIED! EQU 0 (
    echo Release APK not found in "%RELEASE_DIR%".
    exit /b 1
)

endlocal
