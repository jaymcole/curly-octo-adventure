@echo off
REM Script to launch all 4 test clients for local multiplayer testing

echo Starting all 4 test clients...
echo.

cd lwjgl3\build\libs

start "TestClient1" java -jar CurlyOctoAdventure-1.0.0-client1.jar
timeout /t 1 /nobreak >nul

start "TestClient2" java -jar CurlyOctoAdventure-1.0.0-client2.jar
timeout /t 1 /nobreak >nul

start "TestClient3" java -jar CurlyOctoAdventure-1.0.0-client3.jar
timeout /t 1 /nobreak >nul

start "TestClient4" java -jar CurlyOctoAdventure-1.0.0-client4.jar

echo.
echo All 4 test clients launched!
echo Each window is labeled with its client name.
