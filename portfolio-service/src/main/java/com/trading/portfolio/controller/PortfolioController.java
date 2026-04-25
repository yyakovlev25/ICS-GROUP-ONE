package com.trading.portfolio.controller;

import com.trading.portfolio.service.PortfolioService;
import com.trading.shared.dto.OrderValidationRequest;
import com.trading.shared.dto.OrderValidationResult;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class PortfolioController {

    private static final Logger log = LoggerFactory.getLogger(PortfolioController.class);

    private final PortfolioService service;

    public PortfolioController(PortfolioService service) {
        this.service = service;
    }

    public void register(Javalin app) {
        app.get("/health",                          this::health);
        app.get("/customers",                       this::getAllCustomers);
        app.get("/customers/{customerId}",          this::getCustomer);
        app.get("/accounts",                        this::getAllAccounts);
        app.get("/accounts/{accountId}",            this::getAccount);
        app.get("/accounts/{accountId}/holdings",   this::getHoldings);
        app.get("/accounts/{accountId}/portfolio",  this::getPortfolio);
        app.post("/validate",                       this::validateOrder);
    }

    private void health(Context ctx) {
        ctx.json(Map.of("status", "UP", "service", "portfolio-service"));
    }

    private void getAllCustomers(Context ctx) {
        service.getAllCustomers()
                .ifPresentOrElse(
                        ctx::json,
                        () -> ctx.status(502).json(Map.of("error", "PMS not reachable"))
                );
    }

    private void getCustomer(Context ctx) {
        String id = ctx.pathParam("customerId");
        service.getCustomer(id)
                .ifPresentOrElse(
                        ctx::json,
                        () -> ctx.status(404).json(Map.of("error", "Customer not found: " + id))
                );
    }

    private void getAllAccounts(Context ctx) {
        try {
            ctx.json(service.getAllAccounts());
        } catch (Exception e) {
            log.error("getAllAccounts failed", e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void getAccount(Context ctx) {
        String id = ctx.pathParam("accountId");
        try {
            service.getAccount(id)
                    .ifPresentOrElse(
                            ctx::json,
                            () -> ctx.status(404).json(Map.of("error", "Account not found: " + id))
                    );
        } catch (Exception e) {
            log.error("getAccount failed for {}", id, e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void getHoldings(Context ctx) {
        String accountId = ctx.pathParam("accountId");
        try {
            var portfolio = service.getPortfolio(accountId);
            if (portfolio == null) {
                ctx.status(404).json(Map.of("error", "Account not found: " + accountId));
            } else {
                ctx.json(portfolio.get("holdings"));
            }
        } catch (Exception e) {
            log.error("getHoldings failed for {}", accountId, e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void getPortfolio(Context ctx) {
        String accountId = ctx.pathParam("accountId");
        try {
            var portfolio = service.getPortfolio(accountId);
            if (portfolio == null) {
                ctx.status(404).json(Map.of("error", "Account not found: " + accountId));
            } else {
                ctx.json(portfolio);
            }
        } catch (Exception e) {
            log.error("getPortfolio failed for {}", accountId, e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }

    private void validateOrder(Context ctx) {
        try {
            OrderValidationRequest req = ctx.bodyAsClass(OrderValidationRequest.class);

            if (req.customerId == null || req.customerId.isBlank()) {
                ctx.status(400).json(Map.of("error", "customerId is required"));
                return;
            }
            if (req.accountId == null || req.accountId.isBlank()) {
                ctx.status(400).json(Map.of("error", "accountId is required"));
                return;
            }
            if (req.isin == null || req.isin.isBlank()) {
                ctx.status(400).json(Map.of("error", "isin is required"));
                return;
            }
            if (req.quantity <= 0) {
                ctx.status(400).json(Map.of("error", "quantity must be > 0"));
                return;
            }

            OrderValidationResult result = service.validateOrder(req);
            ctx.status(result.valid ? 200 : 422).json(result);

        } catch (Exception e) {
            log.error("validateOrder failed", e);
            ctx.status(500).json(Map.of("error", e.getMessage()));
        }
    }
}
