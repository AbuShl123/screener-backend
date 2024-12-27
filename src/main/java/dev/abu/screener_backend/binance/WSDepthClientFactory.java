package dev.abu.screener_backend.binance;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class WSDepthClientFactory {

    private final RabbitTemplate rabbitTemplate;

    public void createClient(boolean isSpot, String queue, String... symbols) {
        new WSDepthClient(isSpot, queue, symbols);
    }
}
