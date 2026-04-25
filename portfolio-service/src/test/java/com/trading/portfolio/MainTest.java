package com.trading.portfolio;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.trading.shared.client.PmsClient;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the portfolio service.
 * Uses a stub PmsClient so no running PMS server is needed.
 */
class MainTest {

    static ObjectNode makeCustomer(String id, String status, double eurAmount) {
        ObjectNode customer = JsonNodeFactory.instance.objectNode();
        customer.put("externalCustomerNumber", id);
        customer.put("status", status);
        customer.put("riskProfile", "MEDIUM");
        ArrayNode accounts = customer.putArray("cashAccounts");
        accounts.addObject().put("amount", eurAmount).put("currency", "EUR");
        return customer;
    }

    static class StubPms extends PmsClient {
        StubPms() { super("http://nope"); }
        @Override
        public Optional<JsonNode> getCustomer(String id) {
            return switch (id) {
                case "100001" -> Optional.of(makeCustomer(id, "ACTIVE", 12000));
                case "100002" -> Optional.of(makeCustomer(id, "ACTIVE", 500));
                case "100004" -> Optional.of(makeCustomer(id, "BLOCKED", 100000));
                default -> Optional.empty();
            };
        }
    }

    @Test
    void buy_succeeds_when_cash_is_sufficient() {
        var result = Main.validate(new StubPms(), "100001", "DE0005140008", 10, 100.0, "EUR");
        assertTrue((boolean) result.get("valid"));
    }

    @Test
    void buy_fails_when_cash_is_insufficient() {
        var result = Main.validate(new StubPms(), "100002", "DE0005140008", 10, 100.0, "EUR");
        assertFalse((boolean) result.get("valid"));
        assertTrue(result.get("errors").toString().contains("Insufficient cash"));
    }

    @Test
    void buy_fails_when_customer_is_blocked() {
        var result = Main.validate(new StubPms(), "100004", "DE0005140008", 1, 1.0, "EUR");
        assertFalse((boolean) result.get("valid"));
        assertTrue(result.get("errors").toString().contains("BLOCKED"));
    }

    @Test
    void buy_fails_when_customer_unknown() {
        var result = Main.validate(new StubPms(), "999999", "DE0005140008", 1, 1.0, "EUR");
        assertFalse((boolean) result.get("valid"));
        assertTrue(result.get("errors").toString().contains("not found in PMS"));
    }
}

