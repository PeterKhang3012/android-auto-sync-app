@echo off
setlocal
pushd "%~dp0server"
if errorlevel 1 (
    echo ERROR: Cannot open the server folder.
    pause
    exit /b 1
)
where node >nul 2>&1
if errorlevel 1 goto missing_node
where npm >nul 2>&1
if errorlevel 1 goto missing_node

echo Installing server dependencies from package-lock.json...
call npm ci
if errorlevel 1 goto install_failed

echo Starting server. Keep this window open. Press Ctrl+C to stop.
call npm start
set "run_exit=%errorlevel%"
if not "%run_exit%"=="0" (
    echo ERROR: Server stopped with exit code %run_exit%.
    pause
)
popd
exit /b %run_exit%

:missing_node
echo ERROR: Node.js and npm are required. Install Node.js LTS from https://nodejs.org/
echo Then reopen this terminal and run this script again.
popd
pause
exit /b 1

:install_failed
echo ERROR: Dependency installation failed. Server was not started.
echo Check the npm error above and your network connection.
popd
pause
exit /b 1
