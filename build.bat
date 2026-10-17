@echo off
setlocal
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
set "GRADLE_BIN="

if exist "%ROOT%\gradlew.bat" set "GRADLE_BIN=%ROOT%\gradlew.bat"

if not defined GRADLE_BIN (
  where gradle >nul 2>nul
  if %ERRORLEVEL%==0 set "GRADLE_BIN=gradle"
)

if not defined GRADLE_BIN (
  for /d %%D in ("%USERPROFILE%\.gradle\wrapper\dists\gradle-8.14-bin\*") do (
    if exist "%%D\gradle-8.14\bin\gradle.bat" set "GRADLE_BIN=%%D\gradle-8.14\bin\gradle.bat"
  )
)

if not defined GRADLE_BIN (
  echo Gradle 8.x was not found. Install Gradle or add it to PATH.
  exit /b 1
)

call "%GRADLE_BIN%" -p "%ROOT%" %*
exit /b %ERRORLEVEL%
