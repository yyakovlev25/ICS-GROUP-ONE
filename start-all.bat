@echo off
echo === Starting Trading Platform ===
echo.

set BASE_DIR=%~dp0

echo [1/5] Starting PMS server (:8090)...
start "PMS" cmd /c "cd /d %BASE_DIR%pms-1.0.0 && bin\run.bat"
timeout /t 3 /nobreak >nul

echo [2/5] Starting portfolio-service (:8082)...
start "Portfolio" java -jar "%BASE_DIR%portfolio-service\target\portfolio-service-1.0-SNAPSHOT.jar"
timeout /t 1 /nobreak >nul

echo [3/5] Starting compliance-service (:8084)...
start "Compliance" java -jar "%BASE_DIR%compliance-service\target\compliance-service-1.0-SNAPSHOT.jar"
timeout /t 1 /nobreak >nul

echo [4/5] Starting routing-service (:8086)...
start "Routing" java -jar "%BASE_DIR%routing-service\target\routing-service-1.0-SNAPSHOT.jar"
timeout /t 3 /nobreak >nul

echo [5/5] Starting order-service (:8080)...
start "Order" java -jar "%BASE_DIR%order-service\target\order-service-1.0-SNAPSHOT.jar"
timeout /t 1 /nobreak >nul

echo.
echo === All services started ===
echo.
echo   Frontend:    http://localhost:8080
echo   PMS:         http://localhost:8090/pms
echo   Portfolio:   http://localhost:8082
echo   Compliance:  http://localhost:8084
echo   Routing:     http://localhost:8086
echo.
echo Close this window or press Ctrl+C to stop.
pause

