@echo off
setlocal

echo ===================================================
echo   Starting Data Analysis Platform (Local Dev Mode)
echo ===================================================
echo.

echo [Step 1] Starting Python Executor via Docker...
cd python-executor
start "Python Executor" cmd /c "chcp 65001 > nul & docker-compose up python-executor"
cd ..

echo.
echo [Step 2] Starting Backend via Maven...
cd backend
start "Backend (Spring Boot)" cmd /c "chcp 65001 > nul & set JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8 & mvn spring-boot:run"
cd ..

echo.
echo [Step 3] Starting Frontend via Vite...
cd frontend
start "Frontend (Vue 3)" cmd /c "chcp 65001 > nul & npm run dev"
cd ..

echo.
echo ===================================================
echo Services are starting in separate windows!
echo.
echo - Frontend:        http://localhost:5173 (usually)
echo - Backend API:     http://localhost:8080
echo - Python Executor: http://localhost:8000
echo.
echo Close the newly opened terminal windows to stop the services.
echo ===================================================
pause
