package dev.abu.screener_backend.binance;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

@Slf4j
public abstract class WSBinanceClient {

    protected final String websocketName;
    protected final String wsUrl;
    private StandardWebSocketClient client;

    public WSBinanceClient(String websocketName, String url) {
        this.websocketName = websocketName;
        this.wsUrl = url;
    }

    protected abstract WebSocketHandler getWebSocketHandler();

    public void startWebSocket() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        // Set max message buffer size (10MB)
        container.setDefaultMaxTextMessageBufferSize(10 * 1024 * 1024);
        client = new StandardWebSocketClient(container);
        client.execute(getWebSocketHandler(), this.wsUrl);
    }

    public void reconnect() {
        log.info("{} Attempting reconnection", websocketName);
        client.execute(getWebSocketHandler(), this.wsUrl);
    }

    public String getName() {
        return websocketName;
    }
}