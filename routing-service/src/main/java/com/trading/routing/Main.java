package com.trading.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Routing service with embedded Apache Artemis.
 *
 * 1. Starts an embedded Artemis broker (tcp://localhost:61616)
 * 2. Starts a JMS consumer (simulates external broker receiving orders)
 * 3. Exposes REST API: POST /api/route sends orders as JMS messages
 *
 * REST endpoints:
 *   POST /api/route          -> route order via JMS
 *   GET  /api/orders         -> all routed orders
 *   GET  /api/orders/{id}    -> single order
 */
public class Main {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<Map<String, Object>> ORDER_BOOK = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8086"));
        String brokerUrl = "tcp://localhost:61616";

        // 1. Start embedded Artemis broker
        ArtemisBroker artemis = new ArtemisBroker();
        artemis.start();

        // 2. Start mock broker consumer (listens on queue, logs messages)
        Thread consumerThread = new Thread(new BrokerConsumer(brokerUrl));
        consumerThread.setDaemon(true);
        consumerThread.start();

        // Small delay to let consumer connect
        Thread.sleep(500);

        // 3. Create JMS producer
        OrderProducer producer = new OrderProducer(brokerUrl);

        // 4. Start HTTP server
        Javalin app = Javalin.create(c -> {
            c.showJavalinBanner = false;
            c.plugins.enableCors(cors -> cors.add(it -> it.anyHost()));
        }).start(port);

        app.post("/api/route", ctx -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> req = ctx.bodyAsClass(Map.class);
            Map<String, Object> order = buildOrder(req);

            // Send as JMS message to Artemis
            String jsonMsg = JSON.writeValueAsString(order);
            producer.send(jsonMsg);

            ORDER_BOOK.add(order);
            ctx.status(200).json(order);
        });

        app.get("/api/orders", ctx -> ctx.json(ORDER_BOOK));

        app.get("/api/orders/{id}", ctx -> {
            String id = ctx.pathParam("id");
            ORDER_BOOK.stream()
                    .filter(o -> id.equals(o.get("orderId")))
                    .findFirst()
                    .ifPresentOrElse(ctx::json,
                            () -> ctx.status(404).json(Map.of("error", "Order not found")));
        });

        System.out.println("routing-service running on http://localhost:" + port);
        System.out.println("Artemis broker on tcp://localhost:61616");

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                producer.close();
                artemis.stop();
            } catch (Exception ignored) {}
        }));
    }

    static Map<String, Object> buildOrder(Map<String, Object> req) {
        Map<String, Object> order = new LinkedHashMap<>();
        String orderId = "ORD-" + System.currentTimeMillis();
        order.put("orderId", orderId);
        order.put("customerId", req.getOrDefault("customerId", ""));
        order.put("isin", req.getOrDefault("isin", ""));
        order.put("quantity", req.getOrDefault("quantity", 0));
        order.put("price", req.getOrDefault("price", 0));
        order.put("currency", req.getOrDefault("currency", "EUR"));
        order.put("status", "ROUTED_VIA_JMS");
        order.put("broker", "MockBroker-EU");
        order.put("queue", "orders.broker");
        order.put("timestamp", LocalDateTime.now().format(TS));
        return order;
    }
}

