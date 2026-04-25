package com.trading.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.trading.shared.client.PmsClient;
import io.javalin.Javalin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Portfolio service.
 *
 * All data comes from the PMS server (customer master data including
 * cash accounts and status). No local database.
 *
 * REST endpoints:
 *   GET  /api/portfolio/{customerId}
 *   GET  /api/portfolio/{customerId}/check/{isin}/{quantity}/{price}
 *   POST /validate
 */
public class Main {

    public static void main(String[] args) {
        String pmsUrl = System.getenv().getOrDefault("PMS_BASE_URL", "http://localhost:8090/pms");
        int port      = Integer.parseInt(System.getenv().getOrDefault("PORT", "8082"));
        PmsClient pms = new PmsClient(pmsUrl);

        Javalin app = Javalin.create(c -> c.showJavalinBanner = false).start(port);

        app.get("/api/portfolio/{customerId}", ctx -> {
            String customerId = ctx.pathParam("customerId");
            Map<String, Object> body = customerOverview(pms, customerId);
            ctx.status(body.containsKey("error") ? 404 : 200).json(body);
        });

        app.get("/api/portfolio/{customerId}/check/{isin}/{quantity}/{price}", ctx -> {
            String customerId = ctx.pathParam("customerId");
            String isin       = ctx.pathParam("isin");
            double quantity   = Double.parseDouble(ctx.pathParam("quantity"));
            double price      = Double.parseDouble(ctx.pathParam("price"));
            Map<String, Object> result = validate(pms, customerId, isin, quantity, price, "EUR");
            ctx.status((boolean) result.get("valid") ? 200 : 422).json(result);
        });

        app.post("/validate", ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = ctx.bodyAsClass(Map.class);
            String customerId = String.valueOf(req.get("customerId"));
            String isin       = String.valueOf(req.getOrDefault("isin", ""));
            double quantity   = ((Number) req.getOrDefault("quantity", 0)).doubleValue();
            double price      = ((Number) req.getOrDefault("pricePerUnit", 0)).doubleValue();
            String currency   = String.valueOf(req.getOrDefault("currency", "EUR"));
            Map<String, Object> result = validate(pms, customerId, isin, quantity, price, currency);
            ctx.status((boolean) result.get("valid") ? 200 : 422).json(result);
        });

        System.out.println("portfolio-service running on http://localhost:" + port);
    }

    /** Returns PMS customer data with cash accounts. */
    public static Map<String, Object> customerOverview(PmsClient pms, String customerId) {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("customerId", customerId);

        Optional<JsonNode> customer = pms.getCustomer(customerId);
        if (customer.isEmpty()) {
            overview.put("error", "Customer " + customerId + " not found in PMS");
            return overview;
        }
        JsonNode node = customer.get();
        overview.put("fullName", textOrNull(node, "fullName"));
        overview.put("status", textOrNull(node, "status"));
        overview.put("riskProfile", textOrNull(node, "riskProfile"));
        overview.put("country", textOrNull(node, "country"));
        overview.put("cashAccounts", node.get("cashAccounts"));
        return overview;
    }

    /** Validates a buy order using PMS customer data (status + cash accounts). */
    public static Map<String, Object> validate(PmsClient pms, String customerId,
                                               String isin, double quantity,
                                               double pricePerUnit, String currency) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("isin", isin);
        result.put("quantity", quantity);
        result.put("pricePerUnit", pricePerUnit);
        result.put("currency", currency);

        // 1. Customer must exist in PMS
        Optional<JsonNode> customerOpt = pms.getCustomer(customerId);
        if (customerOpt.isEmpty()) {
            return fail(result, "Customer " + customerId + " not found in PMS");
        }
        JsonNode customer = customerOpt.get();

        // 2. Customer must be ACTIVE
        String status = textOrNull(customer, "status");
        result.put("customerStatus", status);
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return fail(result, "Customer " + customerId + " is " + status + ", trading not allowed");
        }

        // 3. Find matching cash account by currency
        double available = findCashBalance(customer, currency);
        result.put("availableCash", available);
        result.put("availableCurrency", currency);

        double required = quantity * pricePerUnit;
        result.put("required", required);

        if (available < required) {
            return fail(result, "Insufficient cash: need " + required + " " + currency
                    + ", available " + available + " " + currency);
        }

        result.put("valid", true);
        result.put("errors", List.of());
        return result;
    }

    /** Finds the cash balance for a given currency from the PMS cashAccounts array. */
    static double findCashBalance(JsonNode customer, String currency) {
        JsonNode accounts = customer.get("cashAccounts");
        if (accounts == null || !accounts.isArray()) return 0.0;
        for (JsonNode acc : accounts) {
            if (currency.equalsIgnoreCase(acc.path("currency").asText(""))) {
                return acc.path("amount").asDouble(0.0);
            }
        }
        return 0.0;
    }

    static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }

    private static Map<String, Object> fail(Map<String, Object> result, String reason) {
        result.put("valid", false);
        result.put("errors", List.of(reason));
        return result;
    }
}

