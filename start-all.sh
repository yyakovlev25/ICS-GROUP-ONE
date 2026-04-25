#!/bin/bash
set -e

BASE_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== Starting Trading Platform ==="
echo ""

# 1) PMS server
echo "[1/5] Starting PMS server (:8090)..."
cd "$BASE_DIR/pms-1.0.0"
./bin/run.sh &
PMS_PID=$!
sleep 3

# 2) Portfolio service
echo "[2/5] Starting portfolio-service (:8082)..."
java -jar "$BASE_DIR/portfolio-service/target/portfolio-service-1.0-SNAPSHOT.jar" &
PORTFOLIO_PID=$!
sleep 1

# 3) Compliance service
echo "[3/5] Starting compliance-service (:8084)..."
java -jar "$BASE_DIR/compliance-service/target/compliance-service-1.0-SNAPSHOT.jar" &
COMPLIANCE_PID=$!
sleep 1

# 4) Routing service (starts embedded Artemis)
echo "[4/5] Starting routing-service (:8086 + Artemis :61616)..."
java -jar "$BASE_DIR/routing-service/target/routing-service-1.0-SNAPSHOT.jar" &
ROUTING_PID=$!
sleep 3

# 5) Order service + frontend
echo "[5/5] Starting order-service (:8080)..."
java -jar "$BASE_DIR/order-service/target/order-service-1.0-SNAPSHOT.jar" &
ORDER_PID=$!
sleep 1

echo ""
echo "=== All services started ==="
echo ""
echo "  Frontend:    http://localhost:8080"
echo "  PMS:         http://localhost:8090/pms"
echo "  Portfolio:   http://localhost:8082"
echo "  Compliance:  http://localhost:8084"
echo "  Routing:     http://localhost:8086"
echo "  Artemis:     tcp://localhost:61616"
echo ""
echo "Press Ctrl+C to stop all services"
echo ""

# Stop everything on Ctrl+C
trap "echo 'Stopping...'; kill $ORDER_PID $ROUTING_PID $COMPLIANCE_PID $PORTFOLIO_PID $PMS_PID 2>/dev/null; exit 0" INT TERM

# Wait for all
wait

