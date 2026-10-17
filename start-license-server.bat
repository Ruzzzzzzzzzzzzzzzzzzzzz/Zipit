@echo off
setlocal
set "ROOT=%~dp0"
set "JAR=%ROOT%dist\zipit-license-server-1.0.0-all.jar"

if not exist "%JAR%" (
  echo License server jar not found: %JAR%
  echo Run: build.bat build dist
  exit /b 1
)

java -jar "%JAR%" --data "%ROOT%license-data" --open %*
