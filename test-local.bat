@echo off
echo ========================================
echo Testing Spring Boot Application Locally
echo ========================================
echo.

echo [1/3] Cleaning previous builds...
call mvn clean

echo.
echo [2/3] Building application...
call mvn package -DskipTests

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ Build FAILED! Please check the errors above.
    pause
    exit /b 1
)

echo.
echo [3/3] Starting application...
echo.
echo ✅ Application is starting on http://localhost:5000
echo Press Ctrl+C to stop the application
echo.

java -jar target\backend.jar

pause
