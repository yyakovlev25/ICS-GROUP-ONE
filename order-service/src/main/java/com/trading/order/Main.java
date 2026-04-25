package com.trading.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.trading.shared.dto.ComplianceCheckRequest;
import com.trading.shared.dto.OrderValidationRequest;
import com.trading.shared.util.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.*;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

/**
 * Terminal-based order service.
 *
 * Start this AFTER portfolio-service (port 8082) and compliance-service (port 8084) are running,
 * and after the PMS server (port 8090) is running.
 *
 * Usage:  java -jar order-service/target/order-service-1.0-SNAPSHOT.jar
 */
public class Main {

    // ── Service URLs (override via environment variables) ─────────────────────
    static final String PORTFOLIO_URL  = env("PORTFOLIO_URL",  "http://localhost:8082");
    static final String COMPLIANCE_URL = env("COMPLIANCE_URL", "http://localhost:8084");
    static final String PMS_URL        = env("PMS_BASE_URL",   "http://localhost:8090/pms");

    static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    static Connection db;

    // ═════════════════════════════════════════════════════════════════════════
    public static void main(String[] args) throws Exception {
        initDb();
        printBanner();
        checkServices();

        Scanner sc = new Scanner(System.in);
        while (true) {
            printMenu();
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1" -> createOrder(sc);
                case "2" -> listOrders();
                case "3" -> viewPortfolio(sc);
                case "4" -> { println("Goodbye!"); return; }
                default  -> println("  Please enter 1, 2, 3 or 4.");
            }
        }
    }

    // ── Startup ───────────────────────────────────────────────────────────────

    static void printBanner() {
        println("");
        println("================================================");
        println("   Trading Platform  –  Order Service CLI      ");
        println("================================================");
    }

    static void printMenu() {
        println("");
        println("  1.  Create new order");
        println("  2.  List all orders");
        println("  3.  View customer portfolio");
        println("  4.  Exit");
        println("------------------------------------------------");
        print("  Choice: ");
    }

    static void checkServices() {
        println("\nChecking services...");
        ping(PORTFOLIO_URL  + "/health",     "portfolio-service  " + PORTFOLIO_URL);
        ping(COMPLIANCE_URL + "/health",     "compliance-service " + COMPLIANCE_URL);
        ping(PMS_URL        + "/customer/all", "PMS server         " + PMS_URL);
    }

    static void ping(String url, String label) {
        try {
            var req = HttpRequest.newBuilder().uri(URI.create(url)).GET()
                    .timeout(Duration.ofSeconds(3)).build();
            int code = HTTP.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
            println("  [" + (code < 400 ? "OK  " : "WARN") + "]  " + label);
        } catch (Exception e) {
            println("  [DOWN]  " + label + "  (" + e.getMessage() + ")");
        }
    }

    // ── Create order ──────────────────────────────────────────────────────────

    static void createOrder(Scanner sc) throws Exception {
        println("\n--- New Order ---");

        String customerId = ask(sc, "Customer ID       (e.g. 100001)");
        String accountId  = ask(sc, "Account ID        (e.g. ACC-100001)");
        String isin       = ask(sc, "ISIN              (e.g. DE0005140008)");

        // ── Fetch instrument from PMS ─────────────────────────────────────────
        String instrumentName = "Unknown";
        String instrumentType = "UNKNOWN";
        print("  Fetching instrument from PMS... ");
        try {
            JsonNode instr = httpGet(PMS_URL + "/instrument/" + isin);
            if (instr != null) {
                instrumentName = field(instr, "Unknown", "name", "instrumentName", "description");
                instrumentType = field(instr, "UNKNOWN", "type", "productType", "instrumentType", "category");
                println("OK  →  " + instrumentName + "  [" + instrumentType + "]");
            } else {
                println("not found in PMS (proceeding with ISIN only)");
            }
        } catch (Exception e) {
            println("PMS unreachable (" + e.getMessage() + ")");
        }

        // ── Order details ─────────────────────────────────────────────────────
        String side = askChoice(sc, "Side              (BUY / SELL)", "BUY", "SELL");

        double quantity;
        while (true) {
            try { quantity = Double.parseDouble(ask(sc, "Quantity")); break; }
            catch (NumberFormatException e) { println("  Please enter a number."); }
        }

        // ── Fetch price from PMS ──────────────────────────────────────────────
        double price = 0;
        print("  Fetching price from PMS...      ");
        try {
            JsonNode priceNode = httpGet(PMS_URL + "/instrument/price/" + isin + "/EUR");
            if (priceNode != null) {
                // PMS may use different field names – try all common ones
                for (String f : new String[]{"price", "value", "last", "lastPrice", "amount", "currentPrice"}) {
                    if (priceNode.has(f) && priceNode.get(f).asDouble() > 0) {
                        price = priceNode.get(f).asDouble();
                        break;
                    }
                }
                if (price > 0) {
                    println("EUR " + String.format("%.4f", price));
                } else {
                    println("OK but could not read price field. Response: " + priceNode);
                }
            } else {
                println("not found");
            }
        } catch (Exception e) {
            println("PMS unreachable (" + e.getMessage() + ")");
        }

        if (price <= 0) {
            while (true) {
                try {
                    price = Double.parseDouble(ask(sc, "Enter price per unit manually (EUR, 0 = skip cash check)"));
                    break;
                } catch (NumberFormatException e) { println("  Please enter a number."); }
            }
        }

        // ── Risk confirmation for certificates ────────────────────────────────
        boolean riskConfirmed = false;
        boolean isRisky = instrumentType.toUpperCase().matches(".*(CERT|ZERTIF|STRUCT|WARRANT|TURBO|KNOCK).*");
        if (isRisky) {
            println("  ! This instrument type (" + instrumentType + ") requires risk acknowledgement.");
            riskConfirmed = "y".equalsIgnoreCase(askChoice(sc, "Confirm risk?  (y / n)", "y", "n"));
        }

        // ── 1/2  Compliance check ─────────────────────────────────────────────
        println("\n  [1/2] Running compliance check...");
        boolean complianceOk = false;
        String  rejectionReason = null;
        String  orderStatus = "APPROVED";

        try {
            var req = new ComplianceCheckRequest();
            req.customerId   = customerId;
            req.isin         = isin;
            req.side         = side;
            req.quantity     = quantity;
            req.pricePerUnit = price;
            req.riskConfirmed = riskConfirmed;

            JsonNode resp = httpPost(COMPLIANCE_URL + "/check",
                    JsonMapper.INSTANCE.writeValueAsString(req));

            if (resp != null && resp.path("approved").asBoolean(false)) {
                println("       [OK]   Compliance approved");
                complianceOk = true;
            } else if (resp != null) {
                println("       [FAIL] Compliance rejected:");
                resp.path("rejectionReasons").forEach(r -> println("              - " + r.asText()));
                orderStatus     = "COMPLIANCE_REJECTED";
                rejectionReason = resp.path("rejectionReasons").path(0).asText("compliance rejected");
            } else {
                println("       [WARN] Compliance service unreachable – skipping check");
                complianceOk = true; // permissive fallback
            }
        } catch (Exception e) {
            println("       [WARN] Compliance error: " + e.getMessage() + " – skipping");
            complianceOk = true;
        }

        // ── 2/2  Portfolio validation ─────────────────────────────────────────
        if (complianceOk) {
            println("  [2/2] Running portfolio validation...");
            try {
                var req = new OrderValidationRequest();
                req.customerId   = customerId;
                req.accountId    = accountId;
                req.isin         = isin;
                req.side         = side;
                req.quantity     = quantity;
                req.pricePerUnit = price;

                JsonNode resp = httpPost(PORTFOLIO_URL + "/validate",
                        JsonMapper.INSTANCE.writeValueAsString(req));

                if (resp != null && resp.path("valid").asBoolean(false)) {
                    double balance = resp.path("cashBalance").asDouble(-1);
                    String bal = balance >= 0 ? "  cash: EUR " + String.format("%.2f", balance) : "";
                    println("       [OK]   Portfolio valid" + bal);
                } else if (resp != null) {
                    println("       [FAIL] Portfolio rejected:");
                    resp.path("errors").forEach(e -> println("              - " + e.asText()));
                    orderStatus     = "PORTFOLIO_REJECTED";
                    rejectionReason = resp.path("errors").path(0).asText("portfolio validation failed");
                } else {
                    println("       [WARN] Portfolio service unreachable – skipping check");
                }
            } catch (Exception e) {
                println("       [WARN] Portfolio error: " + e.getMessage() + " – skipping");
            }
        }

        // ── Save + print result ───────────────────────────────────────────────
        String orderId   = "ORD-" + System.currentTimeMillis();
        double totalValue = quantity * price;

        saveOrder(orderId, customerId, accountId, isin, instrumentName, instrumentType,
                  side, quantity, price, totalValue, orderStatus, rejectionReason);

        println("");
        println("  ┌─ Order Result ──────────────────────────────────────┐");
        println("  │  Order ID   : " + orderId);
        println("  │  Status     : " + orderStatus);
        println("  │  Customer   : " + customerId + "   Account: " + accountId);
        println("  │  Instrument : " + isin + " – " + instrumentName);
        println("  │  Side       : " + side + "  x" + quantity + "  @ EUR " + String.format("%.4f", price));
        println("  │  Total      : EUR " + String.format("%.2f", totalValue));
        if (rejectionReason != null) {
            println("  │  Reason     : " + rejectionReason);
        }
        println("  └────────────────────────────────────────────────────┘");
    }

    // ── List orders ───────────────────────────────────────────────────────────

    static void listOrders() throws SQLException {
        println("\n--- Order History ---");
        String sql = "SELECT order_id, customer_id, isin, side, quantity, total_value, status, created_at " +
                     "FROM orders ORDER BY created_at DESC";
        try (Statement stmt = db.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            println(String.format("  %-24s %-8s %-14s %-5s %8s %12s  %-22s  %s",
                    "Order ID", "CustID", "ISIN", "Side", "Qty", "Total EUR", "Status", "Created at"));
            println("  " + "─".repeat(110));
            boolean any = false;
            while (rs.next()) {
                any = true;
                println(String.format("  %-24s %-8s %-14s %-5s %8.1f %12.2f  %-22s  %s",
                        rs.getString("order_id"),
                        rs.getString("customer_id"),
                        rs.getString("isin"),
                        rs.getString("side"),
                        rs.getDouble("quantity"),
                        rs.getDouble("total_value"),
                        rs.getString("status"),
                        rs.getString("created_at")));
            }
            if (!any) println("  (no orders yet – create one with option 1)");
        }
    }

    // ── View portfolio ────────────────────────────────────────────────────────

    static void viewPortfolio(Scanner sc) {
        println("\n--- Customer Portfolio ---");
        String accountId = ask(sc, "Account ID  (e.g. ACC-100001)");
        print("  Calling portfolio-service... ");
        try {
            JsonNode resp = httpGet(PORTFOLIO_URL + "/accounts/" + accountId + "/portfolio");
            if (resp == null) {
                println("not found: " + accountId);
                return;
            }
            println("OK");
            println("  Account  : " + resp.path("accountId").asText());
            println("  Customer : " + resp.path("customerId").asText());
            println("  Status   : " + resp.path("status").asText());
            println("  Cash     : EUR " + String.format("%.2f", resp.path("cashBalance").asDouble()));
            println("  Holdings :");
            JsonNode holdings = resp.path("holdings");
            if (holdings.isArray() && holdings.size() > 0) {
                holdings.forEach(h ->
                    println("    " + h.path("isin").asText()
                            + "   quantity: " + h.path("quantity").asDouble()));
            } else {
                println("    (none)");
            }
        } catch (Exception e) {
            println("ERROR: " + e.getMessage());
        }
    }

    // ── H2 database (stores orders in memory) ─────────────────────────────────

    static void initDb() throws SQLException {
        db = DriverManager.getConnection(
                "jdbc:h2:mem:orders;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE");
        try (Statement s = db.createStatement()) {
            s.executeUpdate(
                "CREATE TABLE IF NOT EXISTS orders (" +
                "  order_id         VARCHAR(50)  PRIMARY KEY," +
                "  customer_id      VARCHAR(50)," +
                "  account_id       VARCHAR(50)," +
                "  isin             VARCHAR(20)," +
                "  instrument_name  VARCHAR(200)," +
                "  instrument_type  VARCHAR(50)," +
                "  side             VARCHAR(10)," +
                "  quantity         DOUBLE," +
                "  price_per_unit   DOUBLE," +
                "  total_value      DOUBLE," +
                "  status           VARCHAR(30)," +
                "  rejection_reason VARCHAR(500)," +
                "  created_at       VARCHAR(20)" +
                ")"
            );
        }
    }

    static void saveOrder(String orderId, String customerId, String accountId,
                          String isin, String instrName, String instrType,
                          String side, double qty, double price, double total,
                          String status, String rejectionReason) {
        String sql = "INSERT INTO orders VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        try (PreparedStatement ps = db.prepareStatement(sql)) {
            ps.setString(1,  orderId);
            ps.setString(2,  customerId);
            ps.setString(3,  accountId);
            ps.setString(4,  isin);
            ps.setString(5,  instrName);
            ps.setString(6,  instrType);
            ps.setString(7,  side);
            ps.setDouble(8,  qty);
            ps.setDouble(9,  price);
            ps.setDouble(10, total);
            ps.setString(11, status);
            ps.setString(12, rejectionReason);
            ps.setString(13, LocalDateTime.now().format(TS));
            ps.executeUpdate();
        } catch (SQLException e) {
            println("  [WARN] Could not save order: " + e.getMessage());
        }
    }

    // ── HTTP helpers ──────────────────────────────────────────────────────────

    static JsonNode httpGet(String url) throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .build();
        var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() >= 400) return null;
        return JsonMapper.INSTANCE.readTree(resp.body());
    }

    static JsonNode httpPost(String url, String body) throws Exception {
        var req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .build();
        var resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
        return JsonMapper.INSTANCE.readTree(resp.body());
    }

    // ── Terminal helpers ──────────────────────────────────────────────────────

    static String ask(Scanner sc, String question) {
        print("  " + question + ": ");
        return sc.nextLine().trim();
    }

    static String askChoice(Scanner sc, String question, String... options) {
        while (true) {
            print("  " + question + ": ");
            String input = sc.nextLine().trim();
            for (String opt : options) {
                if (opt.equalsIgnoreCase(input)) return opt.toUpperCase();
            }
            println("  Please enter one of: " + String.join(" / ", options));
        }
    }

    /** Extract first non-blank text value from a list of candidate field names. */
    static String field(JsonNode node, String defaultVal, String... names) {
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText();
        }
        return defaultVal;
    }

    static void println(String s) { System.out.println(s); }
    static void print(String s)   { System.out.print(s); System.out.flush(); }

    static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}
