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
 * Uses a stub PmsClient so the tests do not need a running PMS server.
 */
class MainTest {

    static class StubPms extends PmsClient {
        StubPms() { super("http://nope"); }

        @Override
        public Optional<JsonNode> getInstrument(String isin) {
            if ("DE0005140008".equals(isin) || "US0000000001".equals(isin)) {
                return Optional.of(JsonNodeFactory.instance.objectNode()
                        .put("isin", isin).put("name", "Test " + isin));
            }
            return Optional.empty();
        }

        @Override
        public Optional<JsonNode> getRegulatoryInfo(String isin) {
            ObjectNode reg = JsonNodeFactory.instance.objectNode().put("isin", isin);
            reg.put("isRestricted", "US0000000001".equals(isin));
            return Optional.of(reg);
        }

        @Override
        public Optional<JsonNode> getCustomer(String id) {
            if ("100001".equals(id)) {
                return Optional.of(JsonNodeFactory.instance.objectNode().put("externalId", id));
            }
            return Optional.empty();
        }
    }

    @Test
    void check_approved_for_normal_instrument() {
        Map<String, Object> result = Main.check(new StubPms(), "DE0005140008");
        assertTrue((boolean) result.get("approved"));
        assertEquals(false, result.get("restricted"));
    }

    @Test
    void check_rejected_when_pms_marks_instrument_restricted() {
        Map<String, Object> result = Main.check(new StubPms(), "US0000000001");
        assertFalse((boolean) result.get("approved"));
        assertTrue(result.get("rejectionReasons").toString().contains("restricted"));
    }

    @Test
    void check_rejected_when_isin_unknown() {
        Map<String, Object> result = Main.check(new StubPms(), "XX0000000000");
        assertFalse((boolean) result.get("approved"));
        assertTrue(result.get("rejectionReasons").toString().contains("not found in PMS"));
    }

    @Test
    void combined_check_rejected_when_customer_unknown() {
        Map<String, Object> result = Main.checkCustomerAndInstrument(
                new StubPms(), "999999", "DE0005140008");
        assertFalse((boolean) result.get("approved"));
        assertTrue(result.get("rejectionReasons").toString().contains("Customer"));
    }

    @Test
    void combined_check_approved_when_customer_and_instrument_ok() {
        Map<String, Object> result = Main.checkCustomerAndInstrument(
                new StubPms(), "100001", "DE0005140008");
        assertTrue((boolean) result.get("approved"));
    }
}

