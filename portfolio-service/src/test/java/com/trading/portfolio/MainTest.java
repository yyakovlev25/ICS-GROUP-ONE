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

class MainTest {

    static ObjectNode makeCustomer(String id, String status, Object[][] accounts) {
        ObjectNode c = JsonNodeFactory.instance.objectNode();
        c.put("externalCustomerNumber", id);
        c.put("status", status);
        c.put("riskProfile", "MEDIUM");
        ArrayNode accs = c.putArray("cashAccounts");
        for (Object[] a : accounts) {
            accs.addObject().put("amount", (double) a[0]).put("currency", (String) a[1]);
        }
        return c;
    }

    static class StubPms extends PmsClient {
        StubPms() { super("http://nope"); }
        @Override
        public Optional<JsonNode> getCustomer(String id) {
            return switch (id) {
                case "100001" -> Optional.of(makeCustomer(id, "ACTIVE",
                        new Object[][]{{12000.0, "EUR"}, {5000.0, "GBP"}}));
                case "100002" -> Optional.of(makeCustomer(id, "ACTIVE",
                        new Object[][]{{100000.0, "CHF"}, {30000.0, "USD"}}));
                case "100004" -> Optional.of(makeCustomer(id, "BLOCKED",
                        new Object[][]{{100000.0, "EUR"}}));
                default -> Optional.empty();
            };
        }
    }

    @Test
    void buy_succeeds_with_direct_currency_match() {
        var r = Main.validate(new StubPms(), "100001", "X", 10, 100.0, "EUR");
        assertTrue((boolean) r.get("valid"));
        assertEquals("EUR", r.get("accountUsed"));
    }

    @Test
    void buy_fails_when_insufficient_in_selected_currency() {
        // 100001 has 12000 EUR, order needs 20000 EUR, GBP account has 5000 GBP = 5850 EUR -> not enough either
        var r = Main.validate(new StubPms(), "100001", "X", 200, 100.0, "EUR");
        assertFalse((boolean) r.get("valid"));
    }

    @Test
    void buy_succeeds_with_cross_currency_conversion() {
        // 100002 has no EUR account, but 100000 CHF. Order is 500 EUR.
        // 500 EUR / 1.04 (CHF->EUR rate) = ~481 CHF needed. 100000 CHF available -> ok
        var r = Main.validate(new StubPms(), "100002", "X", 5, 100.0, "EUR");
        assertTrue((boolean) r.get("valid"));
        assertEquals("CHF", r.get("convertedFrom"));
    }

    @Test
    void buy_fails_when_customer_blocked() {
        var r = Main.validate(new StubPms(), "100004", "X", 1, 1.0, "EUR");
        assertFalse((boolean) r.get("valid"));
        assertTrue(r.get("errors").toString().contains("BLOCKED"));
    }

    @Test
    void buy_fails_when_customer_unknown() {
        var r = Main.validate(new StubPms(), "999999", "X", 1, 1.0, "EUR");
        assertFalse((boolean) r.get("valid"));
    }

    @Test
    void spot_rate_conversion_is_correct() {
        // Based on SPOT_RATES_TO_EUR: EUR=1.0, USD=0.85, CHF=1.04, GBP=1.17
        assertEquals(850.0, Main.toEur(1000, "USD"), 0.01);
        assertEquals(1040.0, Main.toEur(1000, "CHF"), 0.01);
        assertEquals(1170.0, Main.toEur(1000, "GBP"), 0.01);
        assertEquals(1000.0, Main.toEur(1000, "EUR"), 0.01);
    }
}

