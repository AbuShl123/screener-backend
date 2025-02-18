package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.analysis.OBMessageHandler;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Setter
@Slf4j
public class WSDepthClient extends WSBinanceClient {

    private final OBMessageHandler messageHandler;

    public WSDepthClient(String name, String url, boolean isSpot, String... symbols) {
        super(name, url);
        this.messageHandler = new OBMessageHandler(name, isSpot, symbols);
    }

    @Override
    protected WebSocketHandler getWebSocketHandler() {
        return new OrderBookHandler();
    }

    private class OrderBookHandler extends TextWebSocketHandler {

        @Override
        public void handleMessage(@NonNull WebSocketSession session, @NonNull WebSocketMessage<?> message) {
            messageHandler.handleMessage(message);
        }

        @Override
        public void afterConnectionEstablished(@NonNull WebSocketSession session) {
            log.info("{} websocket connection established", websocketName);
        }

        @Override
        public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
            log.error("Transport error: {}", exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
            log.info("{} Disconnected from websocket with code {} and reason \"{}\"", websocketName, status.getCode(), status.getReason());
            if (status.getReason() != null || status.getCode() == 1006) {
                reconnect();
            }
        }

    }
}