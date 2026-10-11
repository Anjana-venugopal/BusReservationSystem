@echo off
javaw -jar "%~dp0BusReservationSystem.jar"
if errorlevel 1 (
  echo Java 11 or newer is required. Install it from https://adoptium.net
  pause
)
