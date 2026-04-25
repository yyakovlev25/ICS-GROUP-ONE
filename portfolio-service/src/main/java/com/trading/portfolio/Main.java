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
 * All customer data comes from PMS. Spot rates are hardcoded for
 * currency conversion so orders can be placed in any account currency.
 *
 * REST endpoints:
 *   GET  /api/portfolio/{customerId}
 *   GET  /api/portfolio/{customerId}/check/{isin}/{quantity}/{price}/{currency}
 *   GET  /api/spotrates
 *   POST /validate
 */
public class Main {

    /** Spot rates: how many EUR is 1 unit of the given currency worth */
    static final Map<String, Double> SPOT_RATES_TO_EUR = Map.of(
            "EUR", 1.0,
            "USD", 0.85,
            "CHF", 1.04,
            "GBP", 1.17
    );

    public static void main(String[] args) {
        String pmsUrl = System.getenv().getOrDefault("PMS_BASE_URL", "http://localhost:8090/pms");
        int port      = Integer.parseInt(System.getenv().getOrDefault("PORT", "8082"));
        PmsClient pms = new PmsClient(pmsUrl);

        Javalin app = Javalin.create(c -> c.showJavalinBanner = false).start(port);

        app.get("/api/spotrates", ctx -> ctx.json(SPOT_RATES_TO_EUR));

        app.get("/api/portfolio/{customerId}", ctx -> {
            String customerId = ctx.pathParam("customerId");
            Map<String, Object> body = customerOverview(pms, customerId);
            ctx.status(body.containsKey("error") ? 404 : 200).json(body);
        });

        app.get("/api/portfolio/{customerId}/check/{isin}/{quantity}/{price}/{currency}", ctx -> {
            String customerId = ctx.pathParam("customerId");
            String isin       = ctx.pathParam("isin");
            double quantity   = Double.parseDouble(ctx.pathParam("quantity"));
            double price      = Double.parseDouble(ctx.pathParam("price"));
            String currency   = ctx.pathParam("currency");
            Map<String, Object> result = validate(pms, customerId, isin, quantity, price, currency);
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

    /**
     * Validates a buy order. The price is given in the selected currency.
     * If the customer does not have an account in that currency, we try
     * to convert from another account using spot rates.
     */
    public static Map<String, Object> validate(PmsClient pms, String customerId,
                                               String isin, double quantity,
                                               double pricePerUnit, String currency) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("isin", isin);
        result.put("quantity", quantity);
        result.put("pricePerUnit", pricePerUnit);
        result.put("currency", currency);

        Optional<JsonNode> customerOpt = pms.getCustomer(customerId);
        if (customerOpt.isEmpty()) {
            return fail(result, "Customer " + customerId + " not found in PMS");
        }
        JsonNode customer = customerOpt.get();

        String status = textOrNull(customer, "status");
        result.put("customerStatus", status);
        if (!"ACTIVE".equalsIgnoreCase(status)) {
            return fail(result, "Customer " + customerId + " is " + status + ", trading not allowed");
        }

        double requiredInCurrency = quantity * pricePerUnit;
        result.put("required", requiredInCurrency);

        // Try direct match first
        double directBalance = findCashBalance(customer, currency);
        if (directBalance >= requiredInCurrency) {
            result.put("accountUsed", currency);
            result.put("availableCash", directBalance);
            result.put("valid", true);
            result.put("errors", List.of());
            return result;
        }

        // Try converting from other accounts
        double requiredInEur = toEur(requiredInCurrency, currency);
        JsonNode accounts = customer.get("cashAccounts");
        if (accounts != null && accounts.isArray()) {
            for (JsonNode acc : accounts) {
                String accCcy = acc.path("currency").asText("");
                double accAmount = acc.path("amount").asDouble(0);
                double accInEur = toEur(accAmount, accCcy);
                if (accInEur >= requiredInEur) {
                    double neededInAccCcy = fromEur(requiredInEur, accCcy);
                    result.put("accountUsed", accCcy);
                    result.put("availableCash", accAmount);
                    result.put("convertedFrom", accCcy);
                    result.put("spotRate", SPOT_RATES_TO_EUR.getOrDefault(accCcy, 1.0));
                    result.put("amountInAccountCurrency", Math.round(neededInAccCcy * 100.0) / 100.0);
                    result.put("valid", true);
                    result.put("errors", List.of());
                    return result;
                }
            }
        }

        result.put("availableCash", directBalance);
        return fail(result, "Insufficient funds in any account to cover "
                + requiredInCurrency + " " + currency);
    }

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

    /** Converts amount in the given currency to EUR. */
    static double toEur(double amount, String currency) {
        return amount * SPOT_RATES_TO_EUR.getOrDefault(currency.toUpperCase(), 1.0);
    }

    /** Converts EUR amount to the given currency. */
    static double fromEur(double eurAmount, String currency) {
        double rate = SPOT_RATES_TO_EUR.getOrDefault(currency.toUpperCase(), 1.0);
        return (rate > 0) ? eurAmount / rate : eurAmount;
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

