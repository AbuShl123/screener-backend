package dev.abu.screener_backend.binance;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Setter
@Slf4j
public class WSBinanceOrderBookClient extends WSBinanceClient {
    private RabbitTemplate rabbitTemplate;

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
            log.info("Connected to {}: {}", websocketName, session.getId());
        }

        @Override
        protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
            String payload = message.getPayload();
            String symbol = extractSymbol(payload);
            if (symbol == null) {
                log.error("Couldn't get ticker from payload: {}", payload);
                return;
            }
            rabbitTemplate.convertAndSend(symbol, payload);
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

    public static String extractSymbol(String json) {
        Pattern pattern = Pattern.compile("\"stream\"\\s*:\\s*\"(.*?)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1).split("@")[0];
        }
        return null;
    }
}
