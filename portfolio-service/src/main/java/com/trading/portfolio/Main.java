package com.trading.portfolio;

import com.trading.portfolio.controller.PortfolioController;
import com.trading.portfolio.db.Database;
import com.trading.portfolio.repository.AccountRepository;
import com.trading.portfolio.repository.HoldingRepository;
import com.trading.portfolio.service.PortfolioService;
import com.trading.shared.client.PmsClient;
import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        String pmsUrl = env("PMS_BASE_URL", "http://localhost:8090/pms");
        int    port   = Integer.parseInt(env("PORT", "8082"));

        log.info("Starting portfolio-service on port {} (PMS={})", port, pmsUrl);

        // Infrastructure
        Database db = new Database();
        db.initSchema();

        PmsClient pms = new PmsClient(pmsUrl);
        AccountRepository accountRepo = new AccountRepository(db);
        HoldingRepository holdingRepo = new HoldingRepository(db);
        PortfolioService service = new PortfolioService(pms, accountRepo, holdingRepo);
        PortfolioController controller = new PortfolioController(service);

        // HTTP server
        Javalin app = Javalin.create(cfg -> cfg.showJavalinBanner = false);

        // CORS headers for future frontend use
        app.before(ctx -> {
            ctx.header("Access-Control-Allow-Origin",  "*");
            ctx.header("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            ctx.header("Access-Control-Allow-Headers", "Content-Type");
        });
        app.options("/*", ctx -> ctx.status(204));

        // Global error handler
        app.exception(Exception.class, (e, ctx) -> {
            log.error("Unhandled error", e);
            ctx.status(500).json(java.util.Map.of("error",
                    e.getMessage() != null ? e.getMessage() : "Internal server error"));
        });

        controller.register(app);
        app.start(port);

        log.info("portfolio-service ready → http://localhost:{}", port);
    }

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v != null && !v.isBlank()) ? v : defaultValue;
    }
}
