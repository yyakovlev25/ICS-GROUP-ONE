package com.trading.routing;

import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;

/**
 * Starts an embedded Apache Artemis broker.
 * Acceptor on port 61616 (TCP), web console on port 8161.
 */
public class ArtemisBroker {

    private final EmbeddedActiveMQ broker = new EmbeddedActiveMQ();

    public void start() throws Exception {
        Configuration config = new ConfigurationImpl();
        config.addAcceptorConfiguration("tcp", "tcp://0.0.0.0:61616");
        config.setSecurityEnabled(false);
        config.setPersistenceEnabled(false);
        config.setJournalDirectory("target/artemis/journal");
        config.setBindingsDirectory("target/artemis/bindings");
        config.setLargeMessagesDirectory("target/artemis/largemessages");

        broker.setConfiguration(config);
        broker.start();
        System.out.println("[ARTEMIS] Embedded broker started on tcp://localhost:61616");
    }

    public void stop() throws Exception {
        broker.stop();
    }
}

