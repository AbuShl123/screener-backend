package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.analysis.OBMessageHandler;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collection;

@Getter
@Slf4j
public class WSDepthClient extends WSBinanceClient {

    private final OBMessageHandler messageHandler;
    private final Collection<String> symbols;
    private boolean isTurnedOn = false;
    private boolean isConnected = false;

    public WSDepthClient(String name, String url, OBMessageHandler messageHandler, Collection<String> symbols) {
        super(name, url);
        this.messageHandler = messageHandler;
        this.symbols = symbols;
    }

    @Override
    protected WebSocketHandler getWebSocketHandler() {
        return new OrderBookHandler();
    }

    public void turnOn() {
        isTurnedOn = true;
    }

    private class OrderBookHandler extends TextWebSocketHandler {

        @Override
        public void handleMessage(@NonNull WebSocketSession session, @NonNull WebSocketMessage<?> message) {
            if (isTurnedOn) {
                messageHandler.take(message);
            }
        }

        @Override
        public void afterConnectionEstablished(@NonNull WebSocketSession session) {
            isConnected = true;
            log.info("{} websocket connection established with uri {}", websocketName, wsUrl);
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