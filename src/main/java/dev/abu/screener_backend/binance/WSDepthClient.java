package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.analysis.OBMessageHandler;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Setter
@Slf4j
public class WSDepthClient extends WSBinanceClient {

    private final OBMessageHandler messageHandler;
    private final AtomicReference<WebSocketSession> sessionRef = new AtomicReference<>();

    public WSDepthClient(String name, String url, boolean isSpot, String... symbols) {
        super(name, url);
        this.messageHandler = new OBMessageHandler(name, isSpot, symbols);
    }

    @Override
    protected WebSocketHandler getWebSocketHandler() {
        return new OrderBookHandler();
    }

    public WebSocketSession getSession() {
        return sessionRef.get();
    }

    private class OrderBookHandler extends TextWebSocketHandler {

        @Override
        public void handleMessage(@NonNull WebSocketSession session, @NonNull WebSocketMessage<?> message) {
            messageHandler.handleMessage(message);
        }

        @Override
        public void afterConnectionEstablished(@NonNull WebSocketSession session) {
            sessionRef.set(session);
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