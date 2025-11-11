@echo off
REM Script to launch all 4 test clients for local multiplayer testing

echo Starting all 4 test clients...
echo.

REM --- FIX: Use the batch file's directory (%~dp0) to ensure the path is absolute and reliable. ---
REM %~dp0 returns the drive letter and path of the currently executing script (e.g., C:\...\curly-octo-adventure\)
cd /D "%~dp0lwjgl3\build\libs"
REM ------------------------------------------------------------------------------------------------

REM The 'start' commands will now execute successfully from the correct JARs directory.
start "TestClient1" java -jar CurlyOctoAdventure-1.0.0-client1.jar
ping -n 2 127.0.0.1

start "TestClient2" java -jar CurlyOctoAdventure-1.0.0-client2.jar
ping -n 2 127.0.0.1

start "TestClient3" java -jar CurlyOctoAdventure-1.0.0-client3.jar
ping -n 2 127.0.0.1

start "TestClient4" java -jar CurlyOctoAdventure-1.0.0-client4.jar

echo.
echo All 4 test clients launched!
echo Each window is labeled with its client name.
