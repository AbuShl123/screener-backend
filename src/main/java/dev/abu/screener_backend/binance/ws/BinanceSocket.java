package dev.abu.screener_backend.binance.ws;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class BinanceSocket extends WebSocketAdapter {

    private final static ExecutorService worker = Executors.newSingleThreadExecutor();

    private final String name;
    private final WSMessageHandler messageHandler;
    private final Runnable reconnect;
    @Getter private Session session;

    public BinanceSocket(String name, WSMessageHandler messageHandler, Runnable reconnect) {
        this.name = name;
        this.messageHandler = messageHandler;
        this.reconnect = reconnect;
    }

    @Override
    public void onWebSocketText(String message) {
        worker.submit(() -> messageHandler.handleMessage(message));
    }

    @Override
    public void onWebSocketError(Throwable cause) {
        log.error("[{}] WebSocket error", name, cause);
        reconnect.run();
    }

    @Override
    public void onWebSocketConnect(Session session) {
        log.info("[{}] Binance webSocket connected", name);
        this.session = session;
        session.setIdleTimeout(Duration.ofMinutes(10));
    }

    @Override
    public void onWebSocketClose(int statusCode, String reason) {
        log.warn("[{}] WebSocket closed: {} [{}]", name, reason, statusCode);
        reconnect.run();
    }

    public void sendPing() {
        if (session != null && session.isOpen()) {
            try {
                session.getRemote().sendPing(ByteBuffer.wrap(new byte[0]));
                log.debug("[{}] Sent client ping", name);
            } catch (IOException e) {
                log.error("[{}] Failed to send ping", name, e);
            }
        }
    }

    public void sendText(String message) {
        if (session == null || !session.isOpen()) {
            log.warn("[{}] Cannot send message. Session is closed.", name);
            reconnect.run();
            return;
        }
        try {
            session.getRemote().sendString(message);
        } catch (IOException e) {
            log.error("[{}] Error while sending message", name, e);
        }
    }
}
