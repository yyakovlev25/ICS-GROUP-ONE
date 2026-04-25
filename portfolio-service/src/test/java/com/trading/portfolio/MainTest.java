package com.trading.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.trading.shared.client.PmsClient;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the portfolio service.
 * Uses a stub PmsClient so the tests do not need a running PMS server.
 */
class MainTest {

    static class StubPms extends PmsClient {
        StubPms() { super("http://nope"); }
        @Override
        public Optional<JsonNode> getCustomer(String id) {
            if ("100001".equals(id) || "100002".equals(id) || "100004".equals(id)) {
                return Optional.of(JsonNodeFactory.instance.objectNode().put("externalId", id));
            }
            return Optional.empty();
        }
    }

    @Test
    void buy_succeeds_when_cash_is_sufficient() {
        Map<String, Object> result = Main.validate(new StubPms(), "100001", 10, 100.0);
        assertTrue((boolean) result.get("valid"));
    }

    @Test
    void buy_fails_when_cash_is_insufficient() {
        Map<String, Object> result = Main.validate(new StubPms(), "100002", 10, 100.0);
        assertFalse((boolean) result.get("valid"));
        assertTrue(result.get("errors").toString().contains("Insufficient cash"));
    }

    @Test
    void buy_fails_when_customer_unknown_in_pms() {
        Map<String, Object> result = Main.validate(new StubPms(), "999999", 1, 1.0);
        assertFalse((boolean) result.get("valid"));
        assertTrue(result.get("errors").toString().contains("not found in PMS"));
    }

    @Test
    void buy_fails_when_account_is_empty() {
        Map<String, Object> result = Main.validate(new StubPms(), "100004", 1, 1.0);
        assertFalse((boolean) result.get("valid"));
    }
}

