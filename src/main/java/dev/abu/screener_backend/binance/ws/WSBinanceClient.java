package dev.abu.screener_backend.binance.ws;

import dev.abu.screener_backend.binance.Ticker;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Slf4j
public abstract class WSBinanceClient {

    protected static final String BASE_URL = "wss://stream.binance.com:443/ws";

    /** Short english name of the concrete websocket */
    protected final String websocketName;
    /** Binance WebSocket URL to establish connection */
    private String wsUrl;
    /** Ticker - symbol, used for this request */
    protected final Ticker symbol;
    /**  Session which is connected to Binance itself */
    private WebSocketSession binanceSession;
    /**  Binance WebSocket client to interact with Binance */
    private StandardWebSocketClient client;
    /** Binance executor service */
    private final ExecutorService executor;
    /** Data analyzer to retrieve the largest order book figures */

    public WSBinanceClient(String websocketName, String mapping, Ticker symbol, boolean increaseBufferSize) {
        this.websocketName = "Binance [" + websocketName + "] websocket";
        this.symbol = symbol;
        this.executor = Executors.newSingleThreadExecutor();
        setUrl(mapping);
        setClient(increaseBufferSize);
        attemptConnection();
    }

    protected abstract TextWebSocketHandler getWebSocketHandler();

    private void setClient(boolean increaseBufferSize) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        if (increaseBufferSize) {
            // Set max message buffer size (10MB)
            container.setDefaultMaxTextMessageBufferSize(10 * 1024 * 1024);
        }
        this.client = new StandardWebSocketClient(container);;
    }

    private void setUrl(String mapping) {
        var pattern = BASE_URL + mapping;
        this.wsUrl = pattern.replace("{symbol}", symbol.name().toLowerCase());
    }

    private void attemptConnection() {
        client.execute(getWebSocketHandler(), this.wsUrl);
    }

    protected void reconnect() {
        executor.submit(() -> {
            try {
                TimeUnit.SECONDS.sleep(3);
                attemptConnection();
            } catch (InterruptedException e) {
                log.error("Reconnect failed", e);
                Thread.currentThread().interrupt();
            }
        });
    }

    public void closeConnection() {
        try {
            this.binanceSession.close();
        } catch (Exception e) {
            log.error("Failed to close {} connection", websocketName, e);
        }
    }

    protected void setBinanceSession(WebSocketSession binanceSession) {
        this.binanceSession = binanceSession;
    }
}