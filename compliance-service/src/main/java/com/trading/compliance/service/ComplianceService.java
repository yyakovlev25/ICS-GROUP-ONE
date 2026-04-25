package com.trading.compliance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.trading.shared.client.PmsClient;
import com.trading.shared.dto.ComplianceCheckRequest;
import com.trading.shared.dto.ComplianceCheckResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Compliance rules checked against the PMS instrument and customer data:
 *
 *   1. Instrument must exist in PMS.
 *   2. Customer must exist in PMS and must not be blocked.
 *   3. Instrument must not be marked as restricted in PMS regulatory data.
 *   4. If the instrument is a risky product (CERTIFICATE / structured product),
 *      the customer must have confirmed risk (riskConfirmed = true).
 *   5. Quantity must be positive (basic sanity, caller should already check).
 */
public class ComplianceService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceService.class);

    private final PmsClient pms;

    public ComplianceService(PmsClient pms) {
        this.pms = pms;
    }

    // ── Proxy regulatory info from PMS ────────────────────────────────────────

    public Optional<JsonNode> getRegulatoryInfo(String isin) {
        return pms.getRegulatoryInfo(isin);
    }

    public Optional<JsonNode> getInstrument(String isin) {
        return pms.getInstrument(isin);
    }

    // ── Compliance check ──────────────────────────────────────────────────────

    public ComplianceCheckResult check(ComplianceCheckRequest req) {
        String cid  = req.customerId;
        String isin = req.isin;

        // 1. Basic input validation
        if (cid == null || cid.isBlank())  return ComplianceCheckResult.reject(cid, isin, "customerId is required");
        if (isin == null || isin.isBlank()) return ComplianceCheckResult.reject(cid, isin, "isin is required");
        if (req.quantity <= 0)              return ComplianceCheckResult.reject(cid, isin, "quantity must be > 0");

        // 2. Instrument must exist
        Optional<JsonNode> instrumentOpt = pms.getInstrument(isin);
        if (instrumentOpt.isEmpty()) {
            return ComplianceCheckResult.reject(cid, isin,
                    "Instrument '" + isin + "' not found in PMS – trading not permitted");
        }
        JsonNode instrument = instrumentOpt.get();

        String instrumentName = extractText(instrument, "name", "instrumentName", "description");
        String productType    = extractProductType(instrument);

        // 3. Customer must exist
        Optional<JsonNode> customerOpt = pms.getCustomer(cid);
        if (customerOpt.isEmpty()) {
            return ComplianceCheckResult.reject(cid, isin,
                    "Customer '" + cid + "' not found in PMS");
        }
        JsonNode customer = customerOpt.get();

        // 4. Customer must not be blocked
        String customerStatus = extractText(customer, "status", "customerStatus", "state");
        if (customerStatus != null && isBlockedStatus(customerStatus)) {
            return ComplianceCheckResult.reject(cid, isin,
                    "Customer '" + cid + "' is not eligible for trading (status: " + customerStatus + ")");
        }

        // 5. Fetch regulatory info and check restrictions
        Optional<JsonNode> regOpt = pms.getRegulatoryInfo(isin);
        ComplianceCheckResult result;

        if (regOpt.isPresent()) {
            result = checkRegulatoryRules(req, regOpt.get(), instrumentName, productType);
        } else {
            // PMS regulatory endpoint unavailable – apply conservative rules based on instrument type
            log.warn("Regulatory info unavailable for {} – falling back to instrument-type check", isin);
            result = checkFallbackRules(req, instrument, instrumentName, productType);
        }

        return result;
    }

    // ── Rule evaluation ───────────────────────────────────────────────────────

    private ComplianceCheckResult checkRegulatoryRules(ComplianceCheckRequest req,
                                                        JsonNode reg,
                                                        String instrumentName,
                                                        String productType) {
        String isin = req.isin;
        String cid  = req.customerId;

        // Check if instrument is explicitly restricted
        boolean restricted = extractBoolean(reg, "isRestricted", "restricted", "tradeRestricted");
        if (restricted) {
            return ComplianceCheckResult.reject(cid, isin,
                    "Instrument '" + isin + "' is marked as restricted in PMS regulatory data");
        }

        // Determine effective product type (regulatory info may override basic instrument type)
        String regProductType = extractText(reg, "productType", "instrumentType", "category", "type");
        String effectiveType  = regProductType != null ? regProductType : productType;

        // Risky product check: CERTIFICATE or structured products require risk confirmation
        if (isRiskyProduct(effectiveType) && !req.riskConfirmed) {
            ComplianceCheckResult r = ComplianceCheckResult.reject(cid, isin,
                    "Instrument '" + isin + "' is a " + effectiveType
                            + " – customer must confirm risk acknowledgement (riskConfirmed=true)");
            r.productType = effectiveType;
            r.instrumentName = instrumentName;
            return r;
        }

        // All checks passed
        ComplianceCheckResult r = ComplianceCheckResult.approve(cid, isin, instrumentName, effectiveType);
        if (isRiskyProduct(effectiveType)) {
            r.warnings.add("Risky product type " + effectiveType + " – risk confirmed by customer");
        }
        return r;
    }

    private ComplianceCheckResult checkFallbackRules(ComplianceCheckRequest req,
                                                      JsonNode instrument,
                                                      String instrumentName,
                                                      String productType) {
        if (isRiskyProduct(productType) && !req.riskConfirmed) {
            ComplianceCheckResult r = ComplianceCheckResult.reject(req.customerId, req.isin,
                    "Instrument '" + req.isin + "' appears to be a " + productType
                            + " – risk confirmation required (riskConfirmed=true)");
            r.productType = productType;
            r.instrumentName = instrumentName;
            return r;
        }
        ComplianceCheckResult r = ComplianceCheckResult.approve(req.customerId, req.isin,
                instrumentName, productType);
        r.warnings.add("Regulatory data unavailable – basic instrument-type check applied");
        return r;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String extractProductType(JsonNode node) {
        return extractText(node, "type", "productType", "instrumentType", "category", "assetClass");
    }

    private String extractText(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode v = node.get(field);
            if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText();
        }
        return null;
    }

    private boolean extractBoolean(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode v = node.get(field);
            if (v != null && !v.isNull()) return v.asBoolean();
        }
        return false;
    }

    private boolean isBlockedStatus(String status) {
        String s = status.toUpperCase();
        return s.equals("BLOCKED") || s.equals("INACTIVE") || s.equals("SUSPENDED") || s.equals("CLOSED");
    }

    /**
     * Products considered "risky" that require explicit risk acknowledgement.
     * Covers various naming conventions the PMS might use.
     */
    private boolean isRiskyProduct(String productType) {
        if (productType == null) return false;
        String t = productType.toUpperCase();
        return t.contains("CERTIFICATE") || t.contains("ZERTIFIKAT")
                || t.contains("STRUCTURED") || t.contains("WARRANT")
                || t.contains("KNOCK") || t.contains("TURBO")
                || t.contains("OPTIONSSCHEIN");
    }
}
