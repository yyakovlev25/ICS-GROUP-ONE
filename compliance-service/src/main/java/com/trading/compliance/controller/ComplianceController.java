package com.trading.compliance.controller;

import com.trading.compliance.service.ComplianceService;
import com.trading.shared.dto.ComplianceCheckRequest;
import com.trading.shared.dto.ComplianceCheckResult;
import io.javalin.Javalin;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class ComplianceController {

    private static final Logger log = LoggerFactory.getLogger(ComplianceController.class);

    private final ComplianceService service;

    public ComplianceController(ComplianceService service) {
        this.service = service;
    }

    public void register(Javalin app) {
        app.get("/health",                          this::health);
        app.get("/instruments/{isin}",              this::getInstrument);
        app.get("/instruments/{isin}/regulatory",   this::getRegulatoryInfo);
        app.post("/check",                          this::check);
    }

    private void health(Context ctx) {
        ctx.json(Map.of("status", "UP", "service", "compliance-service"));
    }

    private void getInstrument(Context ctx) {
        String isin = ctx.pathParam("isin");
        service.getInstrument(isin)
                .ifPresentOrElse(
                        ctx::json,
                        () -> ctx.status(404).json(Map.of("error", "Instrument not found: " + isin))
                );
    }

    private void getRegulatoryInfo(Context ctx) {
        String isin = ctx.pathParam("isin");
        service.getRegulatoryInfo(isin)
                .ifPresentOrElse(
                        ctx::json,
                        () -> ctx.status(404).json(Map.of("error",
                                "Regulatory info not found for instrument: " + isin))
                );
    }

    private void check(Context ctx) {
        try {
            ComplianceCheckRequest req = ctx.bodyAsClass(ComplianceCheckRequest.class);
            ComplianceCheckResult result = service.check(req);
            ctx.status(result.approved ? 200 : 422).json(result);
        } catch (Exception e) {
            log.error("Compliance check failed", e);
            ctx.status(500).json(Map.of("error",
                    e.getMessage() != null ? e.getMessage() : "Internal error"));
        }
    }
}
