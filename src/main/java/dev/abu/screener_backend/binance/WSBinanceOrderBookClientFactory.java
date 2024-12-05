package dev.abu.screener_backend.binance;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class WSBinanceOrderBookClientFactory {

    private final RabbitTemplate rabbitTemplate;

    public void createClient(String... symbols) {
        WSBinanceOrderBookClient client = new WSBinanceOrderBookClient(symbols);
        client.setRabbitTemplate(rabbitTemplate);
    }
}
