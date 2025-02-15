package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Setter
@Slf4j
public class WSDepthClient extends WSBinanceClient {

    private static int totalEventCount = 0;
    private static long lastCountUpdate = System.currentTimeMillis();
    private static final Map<String, OrderBook> orderBooks = new HashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean isSpot;

    public WSDepthClient(String name, String url, boolean isSpot, String... symbols) {
        super(name);
        this.isSpot = isSpot;
        this.wsUrl = url;
        for (String symbol : symbols) {
            orderBooks.putIfAbsent(symbol, new OrderBook(symbol, websocketName));
        }
    }

    @Override
    protected WebSocketHandler getWebSocketHandler() {
        return new OrderBookHandler();
    }

    private class OrderBookHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(@NonNull WebSocketSession session) {
            log.info("{} websocket connection established", websocketName);
        }

        @Override
        public void handleMessage(@NonNull WebSocketSession session, @NonNull WebSocketMessage<?> message) {
            long start = System.nanoTime();
            processMessage((String) message.getPayload());
            analyzeEventCount();
            long duration = (System.nanoTime() - start) / 1_000_000;
            if (duration > 3) log.info("{} Processed event in {}ms", websocketName, duration);
        }

        @Override
        public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
            log.error("Transport error: {}", exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
            log.info("Disconnected from {} with code {} and reason \"{}\"", websocketName, status.getCode(), status.getReason());
            if (status.getReason() != null || status.getCode() == 1006) {
                reconnect();
            }
        }

        private static synchronized void analyzeEventCount() {
            totalEventCount++;
            if (System.currentTimeMillis() - lastCountUpdate > 60_000) {
                log.info("Total {} events processed in 1 minute", totalEventCount);
                totalEventCount = 0;
                lastCountUpdate = System.currentTimeMillis();
            }
        }

        private void processMessage(String message) {
            try {
                JsonNode root = mapper.readTree(message);
                JsonNode data = root.get("data");
                JsonNode symbolNode = data.get("s");
                if (symbolNode == null) return;
                String symbol = symbolNode.asText().toLowerCase();
                orderBooks.get(symbol).process(data);
            } catch (Exception e) {
                log.error("{} Failed to read json data - {}", websocketName, e.getMessage());
            }
        }
    }
}