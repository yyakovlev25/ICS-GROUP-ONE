package com.trading.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.trading.shared.client.PmsClient;
import io.javalin.Javalin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Order service.
 *
 * Orchestrates compliance-service and portfolio-service checks,
 * then sends approved orders to the routing-service.
 * Serves the web frontend.
 *
 * REST endpoints:
 *   GET  /api/customers
 *   GET  /api/instruments
 *   GET  /api/customer/{id}
 *   POST /api/order
 */
public class Main {

    public static void main(String[] args) {
        String pmsUrl        = env("PMS_BASE_URL",    "http://localhost:8090/pms");
        String portfolioUrl  = env("PORTFOLIO_URL",   "http://localhost:8082");
        String complianceUrl = env("COMPLIANCE_URL",  "http://localhost:8084");
        String routingUrl    = env("ROUTING_URL",     "http://localhost:8086");
        int port             = Integer.parseInt(env("PORT", "8080"));

        PmsClient pms          = new PmsClient(pmsUrl);
        ServiceClient services = new ServiceClient(portfolioUrl, complianceUrl, routingUrl);

        Javalin app = Javalin.create(c -> {
            c.showJavalinBanner = false;
            c.staticFiles.add("/public");
        }).start(port);

        app.get("/api/customers", ctx ->
                pms.getAllCustomers().ifPresentOrElse(ctx::json,
                        () -> ctx.status(502).json(Map.of("error", "PMS not reachable"))));

        app.get("/api/instruments", ctx ->
                pms.getAllInstruments().ifPresentOrElse(ctx::json,
                        () -> ctx.status(502).json(Map.of("error", "PMS not reachable"))));

        app.get("/api/customer/{id}", ctx ->
                pms.getCustomer(ctx.pathParam("id")).ifPresentOrElse(ctx::json,
                        () -> ctx.status(404).json(Map.of("error", "Customer not found"))));

        app.post("/api/order", ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = ctx.bodyAsClass(Map.class);
            String customerId = String.valueOf(req.get("customerId"));
            String isin       = String.valueOf(req.get("isin"));
            double quantity   = ((Number) req.getOrDefault("quantity", 0)).doubleValue();
            double price      = ((Number) req.getOrDefault("pricePerUnit", 0)).doubleValue();
            String currency   = String.valueOf(req.getOrDefault("currency", "EUR"));

            Map<String, Object> result = placeOrder(services, customerId, isin,
                    quantity, price, currency);
            ctx.status("APPROVED".equals(result.get("orderStatus")) ? 200 : 422).json(result);
        });

        System.out.println("order-service running on http://localhost:" + port);
    }

    /** Orchestrates compliance + portfolio checks, then routes via routing-service. */
    public static Map<String, Object> placeOrder(ServiceClient services,
                                                 String customerId, String isin,
                                                 double quantity, double price,
                                                 String currency) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("isin", isin);
        result.put("quantity", quantity);
        result.put("pricePerUnit", price);
        result.put("currency", currency);

        List<String> reasons = new java.util.ArrayList<>();

        // 1. Compliance check
        Optional<JsonNode> complianceResp = services.checkCompliance(customerId, isin);
        if (complianceResp.isEmpty()) {
            reasons.add("Compliance service not reachable");
        } else {
            JsonNode c = complianceResp.get();
            result.put("complianceResult", c);
            if (!c.path("approved").asBoolean(false)) {
                c.path("rejectionReasons").forEach(r -> reasons.add("Compliance: " + r.asText()));
            }
        }

        // 2. Portfolio check (includes currency + spot rate conversion)
        Optional<JsonNode> portfolioResp = services.checkPortfolio(
                customerId, isin, quantity, price, currency);
        if (portfolioResp.isEmpty()) {
            reasons.add("Portfolio service not reachable");
        } else {
            JsonNode p = portfolioResp.get();
            result.put("portfolioResult", p);
            if (!p.path("valid").asBoolean(false)) {
                p.path("errors").forEach(e -> reasons.add("Portfolio: " + e.asText()));
            }
        }

        // 3. Route via routing-service or reject
        if (reasons.isEmpty()) {
            Map<String, Object> routeReq = Map.of(
                    "customerId", customerId,
                    "isin", isin,
                    "quantity", quantity,
                    "price", price,
                    "currency", currency
            );
            Optional<JsonNode> routeResp = services.routeOrder(routeReq);
            if (routeResp.isEmpty()) {
                reasons.add("Routing service not reachable");
                result.put("orderStatus", "REJECTED");
                result.put("rejectionReasons", reasons);
            } else {
                result.put("orderStatus", "APPROVED");
                result.put("routingResult", routeResp.get());
                result.put("rejectionReasons", List.of());
            }
        } else {
            result.put("orderStatus", "REJECTED");
            result.put("rejectionReasons", reasons);
        }

        return result;
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : fallback;
    }
}

