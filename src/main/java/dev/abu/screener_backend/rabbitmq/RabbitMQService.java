package dev.abu.screener_backend.rabbitmq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RabbitMQService {

    private final static Map<String, Object> args = new HashMap<>();
    private final AmqpAdmin amqpAdmin;
    private final CachingConnectionFactory connectionFactory;

    static {
        args.put("x-message-ttl", 0);
    }

    public void createQueue(String queueName) {
        amqpAdmin.declareQueue(new Queue(queueName, false, true, false, args));
    }

    public DirectMessageListenerContainer createConsumer(MessageListener messageListener, String symbol) {
        DirectMessageListenerContainer container = new DirectMessageListenerContainer(connectionFactory);
        container.setQueueNames(symbol);
        container.setMessageListener(messageListener);
        container.start();
        return container;
    }
}