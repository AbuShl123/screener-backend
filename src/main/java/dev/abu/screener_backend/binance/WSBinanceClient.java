package dev.abu.screener_backend.binance;

import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
public abstract class WSBinanceClient {

    protected static final String BASE_URL = "wss://stream.binance.com:443/";
    /** Short english name of the concrete websocket */
    protected final String websocketName;
    /** Binance WebSocket URL to establish connection */
    protected String wsUrl;
    private StandardWebSocketClient client;

    public WSBinanceClient(String websocketName) {
        this.websocketName = websocketName;
    }

    public WSBinanceClient() {
        this.websocketName = "Binance Order Book";
    }

    protected void setWsUrl(String wsUrl) {
        this.wsUrl = BASE_URL + wsUrl;
    }

    protected void startWebSocket(boolean increaseBufferSize) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        if (increaseBufferSize) {
            // Set max message buffer size (10MB)
            container.setDefaultMaxTextMessageBufferSize(10 * 1024 * 1024);
        }
        client = new StandardWebSocketClient(container);
        client.execute(getWebSocketHandler(), this.wsUrl);
    }

    protected abstract TextWebSocketHandler getWebSocketHandler();
}