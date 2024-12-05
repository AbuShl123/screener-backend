package dev.abu.screener_backend.rabbitmq;

import dev.abu.screener_backend.analysis.OrderBookStream;
import dev.abu.screener_backend.binance.jpa.OrderBookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.stereotype.Service;

import static dev.abu.screener_backend.binance.WSBinanceOrderBookClient.extractSymbol;

@Service
@RequiredArgsConstructor
@Slf4j
public class RabbitMQService {

    private final AmqpAdmin amqpAdmin;
    private final CachingConnectionFactory connectionFactory;
    private final OrderBookService orderBookService;

    public void createQueue(String queueName) {
        amqpAdmin.declareQueue(new Queue(queueName, false, true, true));
    }

    public void createConsumer(String queueName) {
        DirectMessageListenerContainer container = new DirectMessageListenerContainer(connectionFactory);
        container.setQueueNames(queueName);
        container.setMessageListener(getListener());
        container.start();
    }

    public MessageListener getListener() {
        return (Message message) -> {
            String payload = new String(message.getBody());
            String queue = extractSymbol(payload);
            var stream = OrderBookStream.getInstance(queue);
            stream.setOrderBookService(orderBookService);
            stream.buffer(payload);
        };
    }
}
