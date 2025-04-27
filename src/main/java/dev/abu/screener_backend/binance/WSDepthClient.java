package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.analysis.OBMessageHandler;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Collection;

@Getter
@Slf4j
public class WSDepthClient {

    protected final String websocketName;
    protected String wsUrl;
    private StandardWebSocketClient client;

    private final OBMessageHandler messageHandler;
    private final Collection<String> symbols;
    private boolean isTurnedOn = false;
    private boolean isConnected = false;

    public WSDepthClient(String name, String url, OBMessageHandler messageHandler, Collection<String> symbols) {
        this.websocketName = name;
        this.wsUrl = url;
        this.messageHandler = messageHandler;
        this.symbols = symbols;
    }

    public void turnOn() {
        isTurnedOn = true;
    }

    public void startWebSocket() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        // set max buffer size: 10MB
        container.setDefaultMaxTextMessageBufferSize(5 * 1024 * 1024);
        client = new StandardWebSocketClient(container);
        client.execute(new DepthHandler(), this.wsUrl);
    }

    public void reconnect() {
        log.info("{} Attempting reconnection", websocketName);
        client.execute(new DepthHandler(), this.wsUrl);
    }

    public void reconnect(String url) {
        this.wsUrl = url;
        reconnect();
    }

    public String getName() {
        return websocketName;
    }

    private class DepthHandler extends TextWebSocketHandler {

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