package dev.abu.screener_backend.rabbitmq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.OrderBookStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
    private final RabbitTemplate rabbitTemplate;

    static {
        args.put("x-message-ttl", 0);
    }

    public void createQueue(String queueName) {
        amqpAdmin.declareQueue(new Queue(queueName, false, true, false, args));
    }

    public void createBinanceConsumer(String queueName) {
        DirectMessageListenerContainer container = new DirectMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setMessageListener(getListener());
        container.start();
    }

    public DirectMessageListenerContainer createClientConsumer(MessageListener messageListener, String symbol) {
        DirectMessageListenerContainer container = new DirectMessageListenerContainer(connectionFactory);
        container.setQueueNames(symbol);
        container.setMessageListener(messageListener);
        container.start();
        return container;
    }

    public MessageListener getListener() {
        return (Message message) -> {
            try {
                ObjectMapper mapper = new ObjectMapper();
                String[] data = mapper.readValue(message.getBody(), new TypeReference<>() {});
                String symbol = data[0];
                String payload = data[1];
                var stream = OrderBookStream.getInstance(symbol);
                stream.setRabbitTemplate(rabbitTemplate);
                stream.buffer(payload);
            } catch (Exception e) {
                log.error("Couldn't send raw data to OrderBookStream", e);
            }
        };
    }
}
