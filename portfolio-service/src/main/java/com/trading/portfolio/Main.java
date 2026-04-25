package com.trading.portfolio;

import com.trading.shared.client.PmsClient;
import io.javalin.Javalin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Portfolio service.
 *
 * Cash balance per customer is held in-memory; everything else
 * (customer master data) is fetched from the PMS server.
 *
 * REST endpoints:
 *   GET  /api/portfolio
 *   GET  /api/portfolio/{customerId}
 *   GET  /api/portfolio/{customerId}/check/{quantity}/{price}
 *   POST /validate
 */
public class Main {

    /** customerId -> cash balance in EUR */
    static final Map<String, Double> BALANCES = new HashMap<>(Map.of(
            "100001",  50_000.0,
            "100002",     500.0,
            "100003",  25_000.0,
            "100004",       0.0,
            "100005",  15_000.0
    ));

    public static void main(String[] args) {
        String pmsUrl = System.getenv().getOrDefault("PMS_BASE_URL", "http://localhost:8090/pms");
        int port      = Integer.parseInt(System.getenv().getOrDefault("PORT", "8082"));
        PmsClient pms = new PmsClient(pmsUrl);

        Javalin app = Javalin.create(c -> c.showJavalinBanner = false).start(port);

        app.get("/api/portfolio", ctx -> ctx.json(BALANCES));

        app.get("/api/portfolio/{customerId}", ctx -> {
            String customerId = ctx.pathParam("customerId");
            Map<String, Object> body = customerOverview(pms, customerId);
            ctx.status(body.containsKey("error") ? 404 : 200).json(body);
        });

        app.get("/api/portfolio/{customerId}/check/{quantity}/{price}", ctx -> {
            String customerId = ctx.pathParam("customerId");
            double quantity   = Double.parseDouble(ctx.pathParam("quantity"));
            double price      = Double.parseDouble(ctx.pathParam("price"));
            Map<String, Object> result = validate(pms, customerId, quantity, price);
            ctx.status((boolean) result.get("valid") ? 200 : 422).json(result);
        });

        app.post("/validate", ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = ctx.bodyAsClass(Map.class);
            String customerId = String.valueOf(req.get("customerId"));
            double quantity   = ((Number) req.getOrDefault("quantity", 0)).doubleValue();
            double price      = ((Number) req.getOrDefault("pricePerUnit", 0)).doubleValue();
            Map<String, Object> result = validate(pms, customerId, quantity, price);
            ctx.status((boolean) result.get("valid") ? 200 : 422).json(result);
        });

        System.out.println("portfolio-service running on http://localhost:" + port);
    }

    /** Combined PMS customer data and local balance. */
    public static Map<String, Object> customerOverview(PmsClient pms, String customerId) {
        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("customerId", customerId);

        var pmsCustomer = pms.getCustomer(customerId);
        if (pmsCustomer.isEmpty()) {
            overview.put("error", "Customer " + customerId + " not found in PMS");
            return overview;
        }
        overview.put("pmsData", pmsCustomer.get());

        Double balance = BALANCES.get(customerId);
        overview.put("hasLocalAccount", balance != null);
        overview.put("cashBalance", balance != null ? balance : 0.0);
        return overview;
    }

    /** Validates a buy order against the customer's cash balance. */
    public static Map<String, Object> validate(PmsClient pms, String customerId,
                                               double quantity, double pricePerUnit) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("quantity", quantity);
        result.put("pricePerUnit", pricePerUnit);

        if (pms.getCustomer(customerId).isEmpty()) {
            return fail(result, "Customer " + customerId + " not found in PMS");
        }
        Double balance = BALANCES.get(customerId);
        if (balance == null) {
            return fail(result, "No account for customer " + customerId);
        }
        result.put("cashBalance", balance);

        double required = quantity * pricePerUnit;
        result.put("required", required);
        if (balance < required) {
            return fail(result, "Insufficient cash: need " + required + " EUR, available " + balance + " EUR");
        }

        result.put("valid", true);
        result.put("errors", List.of());
        return result;
    }

    private static Map<String, Object> fail(Map<String, Object> result, String reason) {
        result.put("valid", false);
        result.put("errors", List.of(reason));
        return result;
    }
}
