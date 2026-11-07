@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 exit /b 1
if not exist build\test-classes mkdir build\test-classes
javac --release 17 -encoding UTF-8 -proc:none -Xlint:all -Werror -cp build\classes -d build\test-classes tests\SimulationTests.java tests\Week13Tests.java
if errorlevel 1 exit /b 1
java -ea -cp "build\classes;build\test-classes" SimulationTests
if errorlevel 1 exit /b 1
java -ea -cp "build\classes;build\test-classes" Week13Tests
exit /b %errorlevel%
