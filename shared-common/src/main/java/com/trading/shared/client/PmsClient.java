package com.trading.shared.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.trading.shared.util.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * HTTP client for the professor-provided PMS server (localhost:8090).
 *
 * PMS endpoints used:
 *   GET /pms/instrument/all
 *   GET /pms/instrument/{isin}
 *   GET /pms/instrument/{isin}/regulartory   (typo is intentional – matches server path)
 *   GET /pms/instrument/price/{isin}/{currency}
 *   GET /pms/customer/all
 *   GET /pms/customer/{externalId}
 */
public class PmsClient {

    private static final Logger log = LoggerFactory.getLogger(PmsClient.class);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String baseUrl;

    public PmsClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    // ── Instrument endpoints ──────────────────────────────────────────────────

    public Optional<JsonNode> getAllInstruments() {
        return get("/instrument/all");
    }

    public Optional<JsonNode> getInstrument(String isin) {
        return get("/instrument/" + isin);
    }

    /** Path uses the typo present in the PMS server: /regulartory */
    public Optional<JsonNode> getRegulatoryInfo(String isin) {
        return get("/instrument/" + isin + "/regulartory");
    }

    public Optional<JsonNode> getPrice(String isin, String currency) {
        return get("/instrument/price/" + isin + "/" + currency);
    }

    // ── Customer endpoints ────────────────────────────────────────────────────

    public Optional<JsonNode> getAllCustomers() {
        return get("/customer/all");
    }

    public Optional<JsonNode> getCustomer(String externalId) {
        return get("/customer/" + externalId);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Optional<JsonNode> get(String path) {
        String url = baseUrl + path;
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return Optional.of(JsonMapper.INSTANCE.readTree(response.body()));
            }
            log.warn("PMS returned HTTP {} for {}", response.statusCode(), url);
            return Optional.empty();

        } catch (Exception e) {
            log.error("PMS call failed [{}]: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}
