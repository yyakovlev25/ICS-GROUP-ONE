# Trading Platform – ICS Group One

Five services: a web-based **order-service** orchestrates **compliance-service**
and **portfolio-service** checks, then sends approved orders to the
**routing-service**, which forwards them to an external broker (mock).
All master data comes from the **PMS server**.

```
  Browser (localhost:8080)
      │
      ▼
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│ order       │────►│ compliance       │────►│ PMS server      │
│ service     │     │ service (:8084)  │◄────│ (:8090)         │
│ (:8080)     │     └──────────────────┘     │                 │
│             │     ┌──────────────────┐     │ /customer/{id}  │
│             │────►│ portfolio        │────►│ /instrument/... │
│             │     │ service (:8082)  │◄────│                 │
│             │     └──────────────────┘     └─────────────────┘
│  if approved:
│             │     ┌──────────────────┐     ┌─────────────────┐
│             │────►│ routing          │────►│ MockBroker-EU   │
│             │     │ service (:8086)  │     │ (simulated)     │
└─────────────┘     └──────────────────┘     └─────────────────┘
```

## Build

```bash
mvn clean package
```

## Run (5 terminals)

```bash
cd pms-1.0.0 && ./bin/run.sh                                              # PMS
java -jar portfolio-service/target/portfolio-service-1.0-SNAPSHOT.jar      # :8082
java -jar compliance-service/target/compliance-service-1.0-SNAPSHOT.jar    # :8084
java -jar routing-service/target/routing-service-1.0-SNAPSHOT.jar          # :8086
java -jar order-service/target/order-service-1.0-SNAPSHOT.jar              # :8080
```

Open **http://localhost:8080** in browser.

## What each service does

### order-service (:8080)

- Serves web frontend
- Proxies PMS data (customers, instruments) for the UI
- Orchestrates: calls compliance + portfolio, then routing if both pass

### compliance-service (:8084)

- Customer exists and is ACTIVE
- Instrument exists and is not sanctioned
- Customer risk profile >= instrument risk category (LOW < MEDIUM < HIGH)

### portfolio-service (:8082)

- Customer exists and is ACTIVE
- Cash account covers the order amount in the selected currency
- If no direct currency match, converts via spot rates and tries other accounts

### routing-service (:8086)

- Starts an embedded Apache Artemis broker on tcp://localhost:61616
- Receives approved orders via POST /api/route
- Sends each order as a JMS TextMessage to queue `orders.broker`
- A built-in mock broker consumer listens on the same queue and logs received messages
- Stores orders in memory, queryable via GET /api/orders
- In the demo you can see messages flow in the routing-service terminal:
  `[JMS SENT]` when the order is published, `[BROKER RECEIVED]` when the mock consumer picks it up

## Currency and spot rates

Customers can have multiple cash accounts (EUR, CHF, GBP, USD).
The frontend lets you pick which account to use. The portfolio-service
converts between currencies using hardcoded spot rates:

| Currency | Rate to EUR |
| -------- | ----------- |
| EUR      | 1.00        |
| USD      | 0.85        |
| CHF      | 1.04        |
| GBP      | 1.17        |

If the selected currency account has insufficient funds, the service
automatically checks other accounts using these rates.

## REST endpoints

### order-service (:8080)

| Method | Path                 | Purpose                     |
| ------ | -------------------- | --------------------------- |
| GET    | `/`                  | Web frontend                |
| GET    | `/api/customers`     | All customers (PMS proxy)   |
| GET    | `/api/instruments`   | All instruments (PMS proxy) |
| GET    | `/api/customer/{id}` | Single customer (PMS proxy) |
| POST   | `/api/order`         | Place order                 |

### portfolio-service (:8082)

| Method | Path                                                        | Purpose                  |
| ------ | ----------------------------------------------------------- | ------------------------ |
| GET    | `/api/portfolio/{customerId}`                               | Customer + cash accounts |
| GET    | `/api/portfolio/{id}/check/{isin}/{qty}/{price}/{currency}` | Validate order           |
| GET    | `/api/spotrates`                                            | Current spot rates       |

### compliance-service (:8084)

| Method | Path                                           | Purpose               |
| ------ | ---------------------------------------------- | --------------------- |
| GET    | `/api/compliance/instrument/{isin}`            | Instrument check      |
| GET    | `/api/compliance/customer/{customerId}/{isin}` | Full compliance check |

### routing-service (:8086)

| Method | Path               | Purpose           |
| ------ | ------------------ | ----------------- |
| POST   | `/api/route`       | Route an order    |
| GET    | `/api/orders`      | All routed orders |
| GET    | `/api/orders/{id}` | Single order      |

## Project layout

```
shared-common/         PmsClient + JsonMapper
portfolio-service/     Main.java + MainTest.java
compliance-service/    Main.java + MainTest.java
order-service/         Main.java + ServiceClient + Frontend
routing-service/       Main.java + ArtemisBroker + OrderProducer + BrokerConsumer
pms-1.0.0/             PMS server
```
