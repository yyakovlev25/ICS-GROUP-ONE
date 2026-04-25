package com.trading.routing;

import jakarta.jms.*;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;

/**
 * JMS producer that sends order messages to the "orders.broker" queue.
 */
public class OrderProducer implements AutoCloseable {

    private final Connection connection;
    private final Session session;
    private final MessageProducer producer;

    public OrderProducer(String brokerUrl) throws JMSException {
        ActiveMQConnectionFactory factory = new ActiveMQConnectionFactory(brokerUrl);
        connection = factory.createConnection();
        connection.start();
        session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Queue queue = session.createQueue("orders.broker");
        producer = session.createProducer(queue);
        producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);
    }

    public void send(String jsonMessage) throws JMSException {
        TextMessage msg = session.createTextMessage(jsonMessage);
        producer.send(msg);
        System.out.println("[JMS SENT] -> orders.broker: " + jsonMessage);
    }

    @Override
    public void close() throws Exception {
        producer.close();
        session.close();
        connection.close();
    }
}

