package com.trading.compliance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trading.shared.client.PmsClient;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the compliance service.
 * Uses a stub PmsClient so no running PMS server is needed.
 */
class MainTest {

    static class StubPms extends PmsClient {
        StubPms() { super("http://nope"); }

        @Override
        public Optional<JsonNode> getCustomer(String id) {
            return switch (id) {
                case "100001" -> Optional.of(customer("100001", "ACTIVE", "LOW"));
                case "100003" -> Optional.of(customer("100003", "ACTIVE", "HIGH"));
                case "100004" -> Optional.of(customer("100004", "BLOCKED", "MEDIUM"));
                default -> Optional.empty();
            };
        }

        @Override
        public Optional<JsonNode> getInstrument(String isin) {
            if ("XX0000000000".equals(isin)) return Optional.empty();
            return Optional.of(JsonNodeFactory.instance.objectNode()
                    .put("isin", isin).put("name", "Test " + isin));
        }

        @Override
        public Optional<JsonNode> getRegulatoryInfo(String isin) {
            ObjectNode reg = JsonNodeFactory.instance.objectNode().put("isin", isin);
            reg.put("sanctioned", "US0000000001".equals(isin));
            reg.put("riskCategory", "DE0005140008".equals(isin) ? "LOW" : "MEDIUM");
            return Optional.of(reg);
        }

        private ObjectNode customer(String id, String status, String risk) {
            ObjectNode c = JsonNodeFactory.instance.objectNode();
            c.put("externalCustomerNumber", id);
            c.put("fullName", "Test " + id);
            c.put("status", status);
            c.put("riskProfile", risk);
            c.putArray("cashAccounts").addObject().put("amount", 10000).put("currency", "EUR");
            return c;
        }
    }

    @Test
    void instrument_approved_when_not_sanctioned() {
        var result = Main.checkInstrument(new StubPms(), "DE0005140008");
        assertTrue((boolean) result.get("approved"));
        assertEquals(false, result.get("sanctioned"));
    }

    @Test
    void instrument_rejected_when_sanctioned() {
        var result = Main.checkInstrument(new StubPms(), "US0000000001");
        assertFalse((boolean) result.get("approved"));
        assertTrue(result.get("rejectionReasons").toString().contains("sanctioned"));
    }

    @Test
    void instrument_rejected_when_unknown() {
        var result = Main.checkInstrument(new StubPms(), "XX0000000000");
        assertFalse((boolean) result.get("approved"));
    }

    @Test
    void combined_approved_when_all_ok() {
        // 100003 has HIGH risk profile, instrument riskCategory is MEDIUM -> ok
        var result = Main.checkCustomerAndInstrument(new StubPms(), "100003", "DE0005140008");
        assertTrue((boolean) result.get("approved"));
    }

    @Test
    void combined_rejected_when_customer_blocked() {
        var result = Main.checkCustomerAndInstrument(new StubPms(), "100004", "DE0005140008");
        assertFalse((boolean) result.get("approved"));
        assertTrue(result.get("rejectionReasons").toString().contains("BLOCKED"));
    }

    @Test
    void combined_rejected_when_risk_too_low() {
        // 100001 has LOW risk profile, CH0038863350 has riskCategory MEDIUM -> rejected
        var result = Main.checkCustomerAndInstrument(new StubPms(), "100001", "CH0038863350");
        assertFalse((boolean) result.get("approved"));
        assertTrue(result.get("rejectionReasons").toString().contains("too low"));
    }

    @Test
    void combined_rejected_when_customer_unknown() {
        var result = Main.checkCustomerAndInstrument(new StubPms(), "999999", "DE0005140008");
        assertFalse((boolean) result.get("approved"));
    }

    @Test
    void combined_rejected_when_instrument_sanctioned() {
        var result = Main.checkCustomerAndInstrument(new StubPms(), "100003", "US0000000001");
        assertFalse((boolean) result.get("approved"));
    }
}

