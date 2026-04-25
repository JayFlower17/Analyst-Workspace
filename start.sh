#!/bin/bash

echo "==================================================="
echo "  Starting Data Analysis Platform (Local Dev Mode)"
echo "==================================================="
echo ""

# Function to run commands in background
run_in_bg() {
    local dir=$1
    local name=$2
    local cmd=$3
    
    echo "[$name] Starting..."
    (cd "$dir" && eval "$cmd") &
}

echo "Step 1: Starting Python Executor via Docker..."
run_in_bg "python-executor" "Python Executor" "docker-compose up python-executor"

echo ""
echo "Step 2: Starting Backend via Maven..."
run_in_bg "backend" "Backend (Spring Boot)" "mvn spring-boot:run"

echo ""
echo "Step 3: Starting Frontend via Vite..."
run_in_bg "frontend" "Frontend (Vue 3)" "npm run dev"

echo ""
echo "==================================================="
echo "Services are starting in the background!"
echo ""
echo "- Frontend:        http://localhost:5173"
echo "- Backend API:     http://localhost:8080"
echo "- Python Executor: http://localhost:8000"
echo ""
echo "To stop all services, press Ctrl+C or kill the terminal."
echo "==================================================="

# Wait for all background processes
wait
