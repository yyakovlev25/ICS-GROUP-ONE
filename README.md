# Trading Platform – ICS Group One

Two small microservices that fetch master data from the PMS server and run
simple business checks on top of it. No database, everything in-memory.

```
                ┌─────────────────────────────┐
                │  PMS server                 │
                │  http://localhost:8090/pms  │
                │   /customer/{id}            │
                │   /instrument/{isin}        │
                │   /instrument/{isin}/...    │
                └─────────────┬───────────────┘
                              │ HTTP GET
              ┌───────────────┴───────────────┐
              │                               │
   ┌──────────▼──────────┐         ┌──────────▼──────────┐
   │ portfolio-service   │         │ compliance-service  │
   │ Port 8082           │         │ Port 8084           │
   │ - local cash map    │         │ - PMS instrument    │
   │ - PMS customer data │         │ - PMS regulatory    │
   │                     │         │ - PMS customer data │
   └─────────────────────┘         └─────────────────────┘
```

Customer data comes from the PMS customer endpoint.
Instrument data and the restricted flag come from the PMS instrument and
regulatory endpoints. The only thing kept locally is the cash balance per
customer in the portfolio service.

## REST API

All endpoints return JSON. GET endpoints can be opened directly in a browser.

### portfolio-service – `http://localhost:8082`

| Method | Path                                                   | Purpose |
|--------|--------------------------------------------------------|---------|
| GET    | `/health`                                              | Health check |
| GET    | `/api/portfolio`                                       | All cash balances |
| GET    | `/api/portfolio/{customerId}`                          | PMS customer data + local balance |
| GET    | `/api/portfolio/{customerId}/check/{quantity}/{price}` | Validate a buy order |
| POST   | `/validate`                                            | Same check via JSON body |

### compliance-service – `http://localhost:8084`

| Method | Path                                             | Purpose |
|--------|--------------------------------------------------|---------|
| GET    | `/health`                                        | Health check |
| GET    | `/api/compliance/instrument/{isin}`              | Verdict for a single instrument |
| GET    | `/api/compliance/customer/{customerId}/{isin}`   | Combined customer + instrument check |
| POST   | `/check`                                         | Same check via JSON body |

## Prerequisites

- Java 21
- Maven 3.8+
- PMS server running at `http://localhost:8090/pms`

## Build and test

```bash
mvn clean package
```

JUnit tests run automatically. They use a stub PmsClient, so no PMS server is
required for testing.

## Run

```bash
# 1) PMS server
cd pms-1.0.0 && ./bin/run.sh

# 2) portfolio-service
java -jar portfolio-service/target/portfolio-service-1.0-SNAPSHOT.jar

# 3) compliance-service
java -jar compliance-service/target/compliance-service-1.0-SNAPSHOT.jar
```

## Demo scenarios

### A) Customer overview
```
http://localhost:8082/api/portfolio/100001
```
Returns the customer JSON from PMS plus the local cash balance.

### B) Validate a buy order

Customer 100001 has 50,000 EUR, so 10 units at 100 EUR is fine:
```
http://localhost:8082/api/portfolio/100001/check/10/100
```

Customer 100002 only has 500 EUR, so the same order fails:
```
http://localhost:8082/api/portfolio/100002/check/10/100
```

Unknown customer:
```
http://localhost:8082/api/portfolio/999999/check/1/1
```

### C) Instrument check

Normal instrument:
```
http://localhost:8084/api/compliance/instrument/DE0005140008
```

Instrument flagged as restricted by PMS:
```
http://localhost:8084/api/compliance/instrument/US0000000001
```

Unknown ISIN:
```
http://localhost:8084/api/compliance/instrument/XX0000000000
```

### D) Combined check

Approved (customer and instrument both ok):
```
http://localhost:8084/api/compliance/customer/100001/DE0005140008
```

Rejected (instrument restricted):
```
http://localhost:8084/api/compliance/customer/100001/US0000000001
```

Rejected (customer unknown):
```
http://localhost:8084/api/compliance/customer/999999/DE0005140008
```

## How a check works

1. Browser or curl sends a GET request to one of our services
2. The service calls the PMS server to fetch the relevant master data
3. The service applies its rule
   - portfolio-service: `cashBalance >= quantity * pricePerUnit`
   - compliance-service: PMS regulatory flag `isRestricted` / `restricted` /
     `tradeRestricted` / `sanctioned`
4. The service responds with HTTP 200 and `valid`/`approved = true`,
   or HTTP 422 and a rejection reason

## Demo data

Cash balances (in `portfolio-service/.../Main.java`):

| customerId | balance |
|-----------|---------|
| 100001    | 50,000  |
| 100002    |    500  |
| 100003    | 25,000  |
| 100004    |      0  |
| 100005    | 15,000  |

The restricted flag is read from the PMS regulatory endpoint
(`GET /pms/instrument/{isin}/regulartory`); nothing is hardcoded.

Known PMS test ISINs: `DE0005140008`, `CH0038863350`, `LU0392494562`,
`DE000CBK1001`, `CH0012453913`, `DE000C1A2BC3`, `US4592001014`,
`US0000000001`, `IE00B4L5Y983`

Known PMS customer ids: `100001`, `100002`, `100003`, `100004`, `100005`

## Project layout

```
shared-common/         PmsClient + JsonMapper
portfolio-service/     Main.java + MainTest.java
compliance-service/    Main.java + MainTest.java
pms-1.0.0/             provided server
```

