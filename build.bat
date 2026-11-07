@echo off
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
javac --release 17 -encoding UTF-8 -proc:none -Xlint:all -Werror -implicit:class -cp build\classes -sourcepath src -d build\classes src\App.java
if errorlevel 1 exit /b 1
echo Build passed (Java 17). 1>&2
