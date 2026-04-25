# Trading Platform – ICS Group One

A hackathon prototype implementing a **Portfolio Service** and a **Compliance Service** that validate trades against the professor-provided **PMS server**.

---

## Architecture

```
PMS Server (provided by professor)
  http://localhost:8090/pms
        │
        ├── /instrument/...    ← market data + regulatory info
        └── /customer/...      ← customer master data
              │
    ┌─────────┴──────────┐
    │                    │
portfolio-service   compliance-service
  :8082                :8084
  (SQLite for          (stateless –
   accounts +           calls PMS only)
   holdings)
```

### portfolio-service (port 8082)
- Proxies customer data from PMS
- Maintains local accounts + holdings in SQLite (seeded on first start)
- `POST /validate` checks whether an order can be placed:
  - customer must exist and be active in PMS
  - account must exist locally and be ACTIVE (not BLOCKED)
  - BUY: cash balance must cover `quantity × pricePerUnit`
  - SELL: holdings must be sufficient

### compliance-service (port 8084)
- Stateless – calls PMS for every check
- `POST /check` evaluates regulatory compliance:
  - instrument must exist in PMS
  - customer must exist and not be blocked
  - if PMS regulatory data marks the instrument as restricted → rejected
  - if instrument type is CERTIFICATE / structured product → `riskConfirmed=true` required

---

## Prerequisites

- Java 21
- Maven 3.8+
- PMS server running at `http://localhost:8090/pms`

---

## Build

```bash
mvn clean package -q
```

This produces two executable fat JARs:
- `portfolio-service/target/portfolio-service-1.0-SNAPSHOT.jar`
- `compliance-service/target/compliance-service-1.0-SNAPSHOT.jar`

---

## Run

### 1. Start the PMS server (professor-provided)

```bash
cd pms-1.0.0
./bin/run.sh        # macOS / Linux
# or
bin\run.bat         # Windows
```

PMS runs at `http://localhost:8090/pms`.

### 2. Start portfolio-service

```bash
java -jar portfolio-service/target/portfolio-service-1.0-SNAPSHOT.jar
```

Runs on port **8082**. SQLite database file `portfolio.db` is created in the working directory on first start (schema + seed data are inserted automatically).

### 3. Start compliance-service

```bash
java -jar compliance-service/target/compliance-service-1.0-SNAPSHOT.jar
```

Runs on port **8084**.

### Environment variables (optional)

| Variable        | Default                       | Purpose                         |
|-----------------|-------------------------------|---------------------------------|
| `PMS_BASE_URL`  | `http://localhost:8090/pms`   | PMS server base URL             |
| `PORT`          | `8082` / `8084`               | Override listening port         |
| `DB_PATH`       | `./portfolio.db`              | SQLite file path (portfolio)    |

---

## Demo Data (seeded automatically)

### Accounts

| Account ID   | Customer ID | Cash Balance | Status  | Notes                        |
|--------------|-------------|--------------|---------|------------------------------|
| ACC-100001   | 100001      | €50,000      | ACTIVE  | Well-funded, has holdings    |
| ACC-100002   | 100002      | €500         | ACTIVE  | Low funds – BUY will fail    |
| ACC-100003   | 100003      | €25,000      | ACTIVE  | Medium funds, has holdings   |
| ACC-100004   | 100004      | €100,000     | BLOCKED | All orders rejected          |
| ACC-100005   | 100005      | €15,000      | ACTIVE  | Holds certificate DE000C1A2BC3 |

### Holdings

| Account ID   | ISIN           | Quantity |
|--------------|----------------|----------|
| ACC-100001   | DE0005140008   | 100      |
| ACC-100001   | CH0038863350   | 50       |
| ACC-100003   | DE0005140008   | 200      |
| ACC-100003   | LU0392494562   | 30       |
| ACC-100005   | DE000C1A2BC3   | 25       |

---

## API Reference

### portfolio-service – `http://localhost:8082`

| Method | Path                              | Description                                |
|--------|-----------------------------------|--------------------------------------------|
| GET    | `/health`                         | Health check                               |
| GET    | `/customers`                      | All customers (proxied from PMS)           |
| GET    | `/customers/{customerId}`         | Single customer (proxied from PMS)         |
| GET    | `/accounts`                       | All local accounts                         |
| GET    | `/accounts/{accountId}`           | Single account                             |
| GET    | `/accounts/{accountId}/holdings`  | Holdings for an account                    |
| GET    | `/accounts/{accountId}/portfolio` | Account + holdings combined                |
| POST   | `/validate`                       | Validate order against portfolio           |

**POST /validate – request body**

```json
{
  "customerId":   "100001",
  "accountId":    "ACC-100001",
  "isin":         "DE0005140008",
  "side":         "BUY",
  "quantity":     10,
  "pricePerUnit": 15.50
}
```

**POST /validate – response (valid)**

```json
{
  "valid":         true,
  "customerId":    "100001",
  "accountId":     "ACC-100001",
  "accountStatus": "ACTIVE",
  "cashBalance":   50000.0,
  "errors":        [],
  "warnings":      []
}
```

---

### compliance-service – `http://localhost:8084`

| Method | Path                              | Description                                |
|--------|-----------------------------------|--------------------------------------------|
| GET    | `/health`                         | Health check                               |
| GET    | `/instruments/{isin}`             | Instrument data (proxied from PMS)         |
| GET    | `/instruments/{isin}/regulatory`  | Regulatory info (proxied from PMS)         |
| POST   | `/check`                          | Run compliance check for an order          |

**POST /check – request body**

```json
{
  "customerId":   "100001",
  "isin":         "DE000C1A2BC3",
  "side":         "BUY",
  "quantity":     5,
  "pricePerUnit": 100.0,
  "riskConfirmed": false
}
```

**POST /check – response (rejected)**

```json
{
  "approved":         false,
  "customerId":       "100001",
  "isin":             "DE000C1A2BC3",
  "productType":      "CERTIFICATE",
  "rejectionReasons": ["Instrument 'DE000C1A2BC3' is a CERTIFICATE – customer must confirm risk acknowledgement (riskConfirmed=true)"],
  "warnings":         []
}
```

---

## Example Scenarios

### Scenario 1 – Successful BUY (well-funded customer)

```bash
curl -s -X POST http://localhost:8082/validate \
  -H "Content-Type: application/json" \
  -d '{"customerId":"100001","accountId":"ACC-100001","isin":"DE0005140008","side":"BUY","quantity":10,"pricePerUnit":15.0}'
```

Expected: `valid: true`

---

### Scenario 2 – BUY rejected (insufficient funds)

```bash
curl -s -X POST http://localhost:8082/validate \
  -H "Content-Type: application/json" \
  -d '{"customerId":"100002","accountId":"ACC-100002","isin":"DE0005140008","side":"BUY","quantity":100,"pricePerUnit":15.0}'
```

Expected: `valid: false` – "Insufficient cash"

---

### Scenario 3 – BUY rejected (blocked account)

```bash
curl -s -X POST http://localhost:8082/validate \
  -H "Content-Type: application/json" \
  -d '{"customerId":"100004","accountId":"ACC-100004","isin":"DE0005140008","side":"BUY","quantity":1,"pricePerUnit":15.0}'
```

Expected: `valid: false` – "Account is BLOCKED"

---

### Scenario 4 – SELL approved (has sufficient holdings)

```bash
curl -s -X POST http://localhost:8082/validate \
  -H "Content-Type: application/json" \
  -d '{"customerId":"100001","accountId":"ACC-100001","isin":"DE0005140008","side":"SELL","quantity":50,"pricePerUnit":0}'
```

Expected: `valid: true`

---

### Scenario 5 – Compliance rejected (certificate, no risk confirmation)

```bash
curl -s -X POST http://localhost:8084/check \
  -H "Content-Type: application/json" \
  -d '{"customerId":"100001","isin":"DE000C1A2BC3","side":"BUY","quantity":5,"pricePerUnit":100.0,"riskConfirmed":false}'
```

Expected: `approved: false` – "must confirm risk acknowledgement"

---

### Scenario 6 – Compliance approved (certificate + risk confirmed)

```bash
curl -s -X POST http://localhost:8084/check \
  -H "Content-Type: application/json" \
  -d '{"customerId":"100001","isin":"DE000C1A2BC3","side":"BUY","quantity":5,"pricePerUnit":100.0,"riskConfirmed":true}'
```

Expected: `approved: true` (with a risk warning)

---

## Supported PMS Test Data

**Instruments (ISIN):** `DE0005140008`, `CH0038863350`, `LU0392494562`, `DE000CBK1001`,
`CH0012453913`, `DE000C1A2BC3`, `US4592001014`, `US0000000001`, `IE00B4L5Y983`

**Customers:** `100001`, `100002`, `100003`, `100004`, `100005`

---

## Limitations

- No Kafka integration (not required for this step)
- No real order lifecycle (orders are not persisted yet)
- Cash balance and holdings are seeded but not updated after trades
- No authentication
- PMS regulatory endpoint path has a known typo: `/regulartory` – the client handles this
