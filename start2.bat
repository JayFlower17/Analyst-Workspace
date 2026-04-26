@echo off
setlocal

echo ===================================================
echo   Starting Data Analysis Platform (New Frontend)
echo ===================================================
echo.

echo [Step 1] Starting Python Executor via Docker...
netstat -ano | findstr ":8000" > nul
if %errorlevel%==0 (
  echo [Step 1] Python Executor port 8000 is already in use. Skipping Python Executor startup.
) else (
  cd python-executor
  start "Python Executor" cmd /c "chcp 65001 > nul & docker-compose up python-executor"
  cd ..
)

echo.
echo [Step 2] Starting Backend via Maven...
netstat -ano | findstr ":8080" > nul
if %errorlevel%==0 (
  echo [Step 2] Backend port 8080 is already in use. Skipping backend startup.
) else (
  cd backend
  start "Backend (Spring Boot)" cmd /c "chcp 65001 > nul & set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 & mvn spring-boot:run -Dspring-boot.run.profiles=local"
  cd ..
)

echo.
echo [Step 3] Starting Frontend via Next.js...
netstat -ano | findstr ":3000" > nul
if %errorlevel%==0 (
  echo [Step 3] Frontend port 3000 is already in use. Skipping frontend startup.
) else (
  cd frontend-next
  start "Frontend (Next.js)" cmd /c "chcp 65001 > nul & npx next dev -H 127.0.0.1 -p 3000"
  cd ..
)

echo.
echo ===================================================
echo Services are starting in separate windows!
echo Only services that are not already running will open new windows.
echo.
echo - Frontend:        http://localhost:3000
echo - Backend API:     http://localhost:8080
echo - Python Executor: http://localhost:8000
echo.
echo Close the newly opened terminal windows to stop the services.
echo ===================================================
pause
