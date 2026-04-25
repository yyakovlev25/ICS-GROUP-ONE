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
 * All data comes from PMS:
 *   /pms/customer/{id}               -> status, riskProfile
 *   /pms/instrument/{isin}           -> instrument data
 *   /pms/instrument/{isin}/regulartory -> sanctioned flag, riskCategory
 *
 * Checks performed:
 *   1. Customer exists and is ACTIVE (not BLOCKED)
 *   2. Instrument exists in PMS
 *   3. Instrument is not sanctioned
 *   4. Customer riskProfile >= instrument riskCategory
 *      (LOW < MEDIUM < HIGH)
 *
 * REST endpoints:
 *   GET  /api/compliance/instrument/{isin}
 *   GET  /api/compliance/customer/{customerId}/{isin}
 *   POST /check
 */
public class Main {

    private static final Map<String, Integer> RISK_LEVELS = Map.of(
            "LOW", 1,
            "MEDIUM", 2,
            "HIGH", 3
    );

    public static void main(String[] args) {
        String pmsUrl = System.getenv().getOrDefault("PMS_BASE_URL", "http://localhost:8090/pms");
        int port      = Integer.parseInt(System.getenv().getOrDefault("PORT", "8084"));
        PmsClient pms = new PmsClient(pmsUrl);

        Javalin app = Javalin.create(c -> c.showJavalinBanner = false).start(port);

        app.get("/api/compliance/instrument/{isin}", ctx -> {
            String isin = ctx.pathParam("isin");
            Map<String, Object> result = checkInstrument(pms, isin);
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
            String customerId = String.valueOf(req.getOrDefault("customerId", ""));
            String isin       = String.valueOf(req.getOrDefault("isin", ""));
            Map<String, Object> result = checkCustomerAndInstrument(pms, customerId, isin);
            ctx.status((boolean) result.get("approved") ? 200 : 422).json(result);
        });

        System.out.println("compliance-service running on http://localhost:" + port);
    }

    /** Checks instrument only: exists + not sanctioned. */
    public static Map<String, Object> checkInstrument(PmsClient pms, String isin) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isin", isin);

        Optional<JsonNode> instrument = pms.getInstrument(isin);
        if (instrument.isEmpty()) {
            return reject(result, "Instrument " + isin + " not found in PMS");
        }
        JsonNode instrNode = instrument.get();
        result.put("instrumentName", textOrNull(instrNode, "name"));
        result.put("assetClass", textOrNull(instrNode, "assetClass"));

        Optional<JsonNode> regulatory = pms.getRegulatoryInfo(isin);
        if (regulatory.isPresent()) {
            JsonNode reg = regulatory.get();
            result.put("sanctioned", reg.path("sanctioned").asBoolean(false));
            result.put("riskCategory", textOrNull(reg, "riskCategory"));
            result.put("mifidCategory", textOrNull(reg, "mifidCategory"));

            if (reg.path("sanctioned").asBoolean(false)) {
                return reject(result, "Instrument " + isin + " is sanctioned");
            }
        }

        result.put("approved", true);
        result.put("rejectionReasons", List.of());
        return result;
    }

    /** Full compliance check: customer status + risk profile vs instrument. */
    public static Map<String, Object> checkCustomerAndInstrument(PmsClient pms,
                                                                 String customerId,
                                                                 String isin) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customerId", customerId);
        result.put("isin", isin);

        // 1. Customer must exist
        Optional<JsonNode> customerOpt = pms.getCustomer(customerId);
        if (customerOpt.isEmpty()) {
            return reject(result, "Customer " + customerId + " not found in PMS");
        }
        JsonNode customer = customerOpt.get();
        String customerStatus = textOrNull(customer, "status");
        String riskProfile    = textOrNull(customer, "riskProfile");
        result.put("customerName", textOrNull(customer, "fullName"));
        result.put("customerStatus", customerStatus);
        result.put("riskProfile", riskProfile);

        // 2. Customer must be ACTIVE
        if (!"ACTIVE".equalsIgnoreCase(customerStatus)) {
            return reject(result, "Customer " + customerId + " is " + customerStatus);
        }

        // 3. Instrument check (exists + not sanctioned)
        Map<String, Object> instrResult = checkInstrument(pms, isin);
        result.put("instrumentCheck", instrResult);

        if (!(boolean) instrResult.get("approved")) {
            return reject(result, "Instrument rejected: " + instrResult.get("rejectionReasons"));
        }

        // 4. Risk profile check: customer riskProfile must be >= instrument riskCategory
        String riskCategory = (String) instrResult.get("riskCategory");
        if (riskProfile != null && riskCategory != null) {
            int customerLevel    = RISK_LEVELS.getOrDefault(riskProfile.toUpperCase(), 0);
            int instrumentLevel  = RISK_LEVELS.getOrDefault(riskCategory.toUpperCase(), 0);
            result.put("customerRiskLevel", customerLevel);
            result.put("instrumentRiskLevel", instrumentLevel);

            if (customerLevel < instrumentLevel) {
                return reject(result, "Customer risk profile " + riskProfile
                        + " is too low for instrument risk category " + riskCategory);
            }
        }

        result.put("approved", true);
        result.put("rejectionReasons", List.of());
        return result;
    }

    static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return (v != null && !v.isNull()) ? v.asText() : null;
    }

    private static Map<String, Object> reject(Map<String, Object> result, String reason) {
        result.put("approved", false);
        result.put("rejectionReasons", List.of(reason));
        return result;
    }
}

