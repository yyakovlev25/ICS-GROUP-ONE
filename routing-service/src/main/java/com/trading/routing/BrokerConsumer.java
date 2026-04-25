package com.trading.routing;

import jakarta.jms.*;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

/**
 * JMS consumer that listens on "orders.broker" and simulates
 * an external broker / market maker receiving the order.
 */
public class BrokerConsumer implements Runnable {

    private final String brokerUrl;

    public BrokerConsumer(String brokerUrl) {
        this.brokerUrl = brokerUrl;
    }

    @Override
    public void run() {
        try {
            ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
            Connection connection = factory.createConnection();
            connection.start();
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue("orders.broker");
            MessageConsumer consumer = session.createConsumer(queue);

            System.out.println("[BROKER] MockBroker-EU listening on queue 'orders.broker'...");

            consumer.setMessageListener(message -> {
                try {
                    if (message instanceof TextMessage txt) {
                        System.out.println("[BROKER RECEIVED] " + txt.getText());
                    }
                } catch (JMSException e) {
                    System.err.println("[BROKER ERROR] " + e.getMessage());
                }
            });

        } catch (JMSException e) {
            System.err.println("[BROKER] Failed to start: " + e.getMessage());
        }
    }
}

