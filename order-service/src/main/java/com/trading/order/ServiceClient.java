package com.trading.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.trading.shared.util.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * HTTP client for calling portfolio-service, compliance-service and routing-service.
 */
public class ServiceClient {

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String portfolioUrl;
    private final String complianceUrl;
    private final String routingUrl;

    public ServiceClient(String portfolioUrl, String complianceUrl, String routingUrl) {
        this.portfolioUrl  = portfolioUrl;
        this.complianceUrl = complianceUrl;
        this.routingUrl    = routingUrl;
    }

    public Optional<JsonNode> checkCompliance(String customerId, String isin) {
        return get(complianceUrl + "/api/compliance/customer/" + customerId + "/" + isin);
    }

    public Optional<JsonNode> checkPortfolio(String customerId, String isin,
                                             double quantity, double price, String currency) {
        String url = portfolioUrl + "/api/portfolio/" + customerId
                + "/check/" + isin + "/" + quantity + "/" + price + "/" + currency;
        return get(url);
    }

    public Optional<JsonNode> routeOrder(Map<String, Object> order) {
        return post(routingUrl + "/api/route", order);
    }

    private Optional<JsonNode> get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url)).GET()
                    .timeout(Duration.ofSeconds(5))
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            return Optional.of(JsonMapper.INSTANCE.readTree(resp.body()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private Optional<JsonNode> post(String url, Object body) {
        try {
            String json = JsonMapper.INSTANCE.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .timeout(Duration.ofSeconds(5))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build();
            HttpResponse<String> resp = http.send(request, HttpResponse.BodyHandlers.ofString());
            return Optional.of(JsonMapper.INSTANCE.readTree(resp.body()));
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}

