package com.trading.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import com.trading.shared.client.PmsClient;
import io.javalin.Javalin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Compliance service.
 *
 * All facts come from the PMS server:
 *   /pms/customer/{customerId}
 *   /pms/instrument/{isin}
 *   /pms/instrument/{isin}/regulartory
 *
 * REST endpoints:
 *   GET  /api/compliance/instrument/{isin}
 *   GET  /api/compliance/customer/{customerId}/{isin}
 *   POST /check
 */
public class Main {

    public static void main(String[] args) {
        String pmsUrl = System.getenv().getOrDefault("PMS_BASE_URL", "http://localhost:8090/pms");
        int port      = Integer.parseInt(System.getenv().getOrDefault("PORT", "8084"));
        PmsClient pms = new PmsClient(pmsUrl);

        Javalin app = Javalin.create(c -> c.showJavalinBanner = false).start(port);

        app.get("/api/compliance/instrument/{isin}", ctx -> {
            String isin = ctx.pathParam("isin");
            Map<String, Object> result = check(pms, isin);
            ctx.status((boolean) result.get("approved") ? 200 : 422).json(result);
        });

        app.get("/api/compliance/customer/{customerId}/{isin}", ctx -> {
            String customerId = ctx.pathParam("customerId");
            String isin       = ctx.pathParam("isin");
            Map<String, Object> result = checkCustomerAndInstrument(pms, customerId, isin);
            ctx.status((boolean) result.get("approved") ? 200 : 422).json(result);
        });

        app.post("/check", ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = ctx.bodyAsClass(Map.class);
            String isin = String.valueOf(req.get("isin"));
            Map<String, Object> result = check(pms, isin);
            ctx.status((boolean) result.get("approved") ? 200 : 422).json(result);
        });

        System.out.println("compliance-service running on http://localhost:" + port);
    }

    /** Verdict for an instrument based on PMS instrument and regulatory data. */
    public static Map<String, Object> check(PmsClient pms, String isin) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isin", isin);

        Optional<JsonNode> instrument = pms.getInstrument(isin);
        if (instrument.isEmpty()) {
            return reject(result, "Instrument " + isin + " not found in PMS");
        }
        JsonNode instrNode = instrument.get();
        if (instrNode.has("name")) result.put("name", instrNode.get("name").asText());
        result.put("pmsInstrument", instrNode);

        Optional<JsonNode> regulatory = pms.getRegulatoryInfo(isin);
        regulatory.ifPresent(node -> result.put("pmsRegulatory", node));

        boolean restricted = regulatory.map(Main::isRestricted).orElse(false);
        result.put("restricted", restricted);

        if (restricted) {
            return reject(result, "Instrument " + isin + " is flagged as restricted by PMS");
        }

        result.put("approved", true);
        result.put("rejectionReasons", List.of());
        return result;
    }

    /** Verdict for a customer trying to trade a specific instrument. */
    public static Map<String, Object> checkCustomerAndInstrument(PmsClient pms,
                                                                 String customerId,
                                                                 String isin) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("isin", isin);

        Optional<JsonNode> customer = pms.getCustomer(customerId);
        if (customer.isEmpty()) {
            return reject(result, "Customer " + customerId + " not found in PMS");
        }
        result.put("pmsCustomer", customer.get());

        Map<String, Object> instrumentResult = check(pms, isin);
        result.put("instrumentCheck", instrumentResult);

        if (!(boolean) instrumentResult.get("approved")) {
            return reject(result, "Instrument check failed: " + instrumentResult.get("rejectionReasons"));
        }

        result.put("approved", true);
        result.put("rejectionReasons", List.of());
        return result;
    }

    /** Reads the restricted flag from PMS regulatory JSON, accepting common field names. */
    static boolean isRestricted(JsonNode regulatory) {
        for (String field : new String[]{"isRestricted", "restricted", "tradeRestricted", "sanctioned"}) {
            JsonNode v = regulatory.get(field);
            if (v != null && !v.isNull() && v.asBoolean()) return true;
        }
        return false;
    }

    private static Map<String, Object> reject(Map<String, Object> result, String reason) {
        result.put("approved", false);
        result.put("rejectionReasons", List.of(reason));
        return result;
    }
}
