package dev.abu.screener_backend.rabbitmq;

import com.fasterxml.jackson.databind.JsonNode;
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

    public DirectMessageListenerContainer createClientConsumer(
            MessageListener messageListener,
            String... symbols
    ) {
        DirectMessageListenerContainer container = new DirectMessageListenerContainer(connectionFactory);
        container.setQueueNames(symbols);
        container.setMessageListener(messageListener);
        container.start();
        return container;
    }

    public MessageListener getListener() {
        return (Message message) -> {
            String payload = new String(message.getBody());
            StringBuilder sb = new StringBuilder(payload);
            sb.deleteCharAt(0);
            sb.deleteCharAt(sb.length() - 1);
            payload = sb.toString().replace("\\", "");
            String symbol = extractSymbol(payload);
            if (symbol == null) {
                log.error("Couldn't get ticker from payload: {}", payload);
                return;
            }
            var stream = OrderBookStream.getInstance(symbol);
            stream.setRabbitTemplate(rabbitTemplate);
            stream.buffer(payload);
        };
    }

    public String extractSymbol(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(json);
            String streamValue = rootNode.get("stream").asText();
            return streamValue.split("@")[0];
        } catch (Exception e) {
            return null;
        }
    }
}
