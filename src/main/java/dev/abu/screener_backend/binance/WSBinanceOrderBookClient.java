package dev.abu.screener_backend.binance;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Setter
@Slf4j
public class WSBinanceOrderBookClient extends WSBinanceClient {

    private RabbitTemplate rabbitTemplate;
    private String queue;

    /**
     * Native Binance WebSocket for Order Book data.
     *
     * @param symbol symbols to connect for
     */
    public WSBinanceOrderBookClient(String... symbol) {
        super("Order Book");
        setWsUrl(symbol);
        startWebSocket(true);
    }

    // TODO: send historical snapshot to the stream.
    private void sendHistoricalData(String... symbols) {

    }

    private void setWsUrl(String... symbols) {
        StringBuilder sb = new StringBuilder("stream?streams=");
        for (String symbol : symbols) {
            sb.append(symbol.toLowerCase()).append("@depth/");
        }
        sb.deleteCharAt(sb.length() - 1);
        setWsUrl(sb.toString());
    }

    @Override
    protected TextWebSocketHandler getWebSocketHandler() {
        return new OrderBookHandler();
    }

    private class OrderBookHandler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(@NonNull WebSocketSession session) {
            log.info("Connected to {}: {} - {}", websocketName, session.getId(), wsUrl);
        }

        @Override
        protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
            String payload = message.getPayload();
            rabbitTemplate.convertAndSend(queue, payload);
        }

        @Override
        public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
            log.error("Transport error: {}", exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
            log.info("Disconnected from {}: {}", websocketName, status.getReason());
        }
    }
}
