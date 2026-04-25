# Trading Platform – ICS Group One

Two microservices that fetch all data from the PMS server and apply
business rules on top of it. No local database, no hardcoded data.

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
   │                     │         │                     │
   │ checks:             │         │ checks:             │
   │ - customer ACTIVE?  │         │ - customer ACTIVE?  │
   │ - enough cash?      │         │ - not sanctioned?   │
   │                     │         │ - risk profile ok?  │
   └─────────────────────┘         └─────────────────────┘
```

### portfolio-service – `http://localhost:8082`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/portfolio/{customerId}` | PMS customer data + cash accounts |
| GET | `/api/portfolio/{customerId}/check/{isin}/{quantity}/{price}` | Validate a buy order |
| POST | `/validate` | Same check via JSON body |

**Portfolio checks:**
- Customer exists in PMS
- Customer status is ACTIVE (not BLOCKED)
- Cash balance in the matching currency covers `quantity * price`

Cash balances come from `PMS /customer/{id}` -> `cashAccounts[]`.

### compliance-service – `http://localhost:8084`

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/compliance/instrument/{isin}` | Instrument check (sanctioned?) |
| GET | `/api/compliance/customer/{customerId}/{isin}` | Full compliance check |
| POST | `/check` | Same check via JSON body |

**Compliance checks:**
- Customer exists in PMS and is ACTIVE
- Instrument exists in PMS
- Instrument is not sanctioned (`sanctioned: true` in PMS regulatory)
- Customer risk profile >= instrument risk category
  - LOW < MEDIUM < HIGH
  - A LOW customer cannot trade MEDIUM or HIGH instruments

## Build and test

```bash
mvn clean package
```

## Run

```bash
cd pms-1.0.0 && ./bin/run.sh
java -jar portfolio-service/target/portfolio-service-1.0-SNAPSHOT.jar
java -jar compliance-service/target/compliance-service-1.0-SNAPSHOT.jar
```

## Demo scenarios

### Portfolio

Customer overview (Alice, ACTIVE, 12000 EUR + 5000 GBP):
```
http://localhost:8082/api/portfolio/100001
```

Buy ok (10 x 100 EUR = 1000, Alice has 12000 EUR):
```
http://localhost:8082/api/portfolio/100001/check/DE0005140008/10/100
```

Buy rejected, insufficient cash (Bob has 100000 CHF but only 30000 USD):
```
http://localhost:8082/api/portfolio/100002/check/DE0005140008/500/100
```

Rejected, customer BLOCKED (David):
```
http://localhost:8082/api/portfolio/100004/check/DE0005140008/1/1
```

Unknown customer:
```
http://localhost:8082/api/portfolio/999999/check/DE0005140008/1/1
```

### Compliance

Normal instrument (not sanctioned):
```
http://localhost:8084/api/compliance/instrument/DE0005140008
```

Sanctioned instrument:
```
http://localhost:8084/api/compliance/instrument/US0000000001
```

Full check – approved (Carla, HIGH risk profile):
```
http://localhost:8084/api/compliance/customer/100003/DE0005140008
```

Rejected – customer BLOCKED (David):
```
http://localhost:8084/api/compliance/customer/100004/DE0005140008
```

Rejected – risk profile too low (Alice is LOW, instrument is MEDIUM):
```
http://localhost:8084/api/compliance/customer/100001/CH0038863350
```

Rejected – sanctioned instrument:
```
http://localhost:8084/api/compliance/customer/100003/US0000000001
```

Unknown customer:
```
http://localhost:8084/api/compliance/customer/999999/DE0005140008
```

## PMS test data

| Customer | Name | Status | Risk Profile | Cash |
|----------|------|--------|-------------|------|
| 100001 | Alice | ACTIVE | LOW | 12000 EUR, 5000 GBP |
| 100002 | Bob | ACTIVE | MEDIUM | 100000 CHF, 30000 USD |
| 100003 | Carla | ACTIVE | HIGH | 55000 EUR |
| 100004 | David | BLOCKED | MEDIUM | 100000 EUR |
| 100005 | Eva | ACTIVE | LOW | 5000 EUR, 330000 CHF, 105000 GBP |

## Project layout

```
shared-common/         PmsClient + JsonMapper
portfolio-service/     Main.java + MainTest.java
compliance-service/    Main.java + MainTest.java
pms-1.0.0/             PMS server
```

