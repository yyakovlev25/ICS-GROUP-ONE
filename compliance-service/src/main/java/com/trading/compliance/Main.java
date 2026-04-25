package com.trading.compliance;

import com.trading.compliance.controller.ComplianceController;
import com.trading.compliance.service.ComplianceService;
import com.trading.shared.client.PmsClient;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String pmsUrl = env("PMS_BASE_URL", "http://localhost:8090/pms");
        int    port   = Integer.parseInt(env("PORT", "8084"));

        log.info("Starting compliance-service on port {} (PMS={})", port, pmsUrl);

        PmsClient pms = new PmsClient(pmsUrl);
        ComplianceService service = new ComplianceService(pms);
        ComplianceController controller = new ComplianceController(service);

        Javalin app = Javalin.create(cfg -> cfg.showJavalinBanner = false);

        // CORS headers for future frontend use
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin",  "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type");
        });
        app.options("/*", ctx -> ctx.status(204));

        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled error", e);
            ctx.status(500).json(Map.of("error",
                    e.getMessage() != null ? e.getMessage() : "Internal server error"));
        });

        controller.register(app);
        app.start(port);

        log.info("compliance-service ready → http://localhost:{}", port);
    }

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }
}
