package dev.abu.screener_backend.binance.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.entities.WSSubscriptionRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.websocket.client.WebSocketClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@RequiredArgsConstructor
@Slf4j
public class WebSocketManager {

    private static final int MAX_BACKOFF_SECONDS = 60;

    // spring beans
    private final WSMessageHandler messageHandler;
    private final ObjectMapper objectMapper;
    private final WebSocketClient client;

    // websocket related fields
    private final URI endpoint;
    private final String name;
    private volatile BinanceSocket socket;
    private final ScheduledExecutorService scheduler;
    private final Set<String> subscribedStreams = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private int reconnectAttempts;

    public WebSocketManager(
            WebSocketClient client,
            URI endpoint,
            String name,
            WSMessageHandler messageHandler,
            ObjectMapper objectMapper,
            ScheduledExecutorService scheduler
    ) {
        this.client = client;
        this.endpoint = endpoint;
        this.name = name;
        this.messageHandler = messageHandler;
        this.objectMapper = objectMapper;
        this.scheduler = scheduler;
    }

    public void start() {
        try {
            reconnectAttempts = 0;
            isConnecting.set(true);
            scheduler.execute(this::connect);
            scheduler.scheduleAtFixedRate(() -> this.socket.sendPing(), 30, 30, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.error("Error starting WebSocket Client", e);
        }
    }

    public boolean isConnected() {
        return (client.isRunning() && !isConnecting.get() && socket != null && socket.getSession() != null && socket.getSession().isOpen());
    }

    private void connect() {
        try {
            BinanceSocket socket = new BinanceSocket(name, messageHandler, this::scheduleReconnect);
            this.socket = socket;

            log.info("[{}] Connecting to WebSocket: {}", name, endpoint);
            client.connect(socket, endpoint).get(60, TimeUnit.SECONDS);

            isConnecting.set(false);
            reconnectAttempts = 0; // reset backoff on success
            subscribe(subscribedStreams); // re-subscribe to previously subscribed streams
        } catch (Throwable e) {
            log.error("[{}] WebSocket connection failed", name, e);
            isConnecting.set(false);
            scheduleReconnect();
        }
    }

    private void scheduleReconnect() {
        if (isConnecting.get()) {
            log.info("Skipping reconnect - isConnecting=true");
            return;
        }

        isConnecting.set(true);
        int delay = (int) Math.min(Math.pow(2, reconnectAttempts), MAX_BACKOFF_SECONDS);
        reconnectAttempts++;

        log.warn("[{}] Reconnecting in {}s (attempt #{})...", name, delay, reconnectAttempts);
        scheduler.schedule(this::connect, delay, TimeUnit.SECONDS);
    }

    public void subscribe(Collection<String> streamSet) {
        List<String> streams = new ArrayList<>(streamSet);
        subscribedStreams.addAll(streams);
        if (streams.isEmpty() || !isConnected()) return;
        try {
            subscribeByChunks(streams);
            log.info("[{}] Subscribed to {} streams", name, streams.size());
        } catch (Exception e) {
            log.error("[{}] Couldn't subscribe to streams", name, e);
        }
    }

    private void subscribeByChunks(List<String> streams) throws Exception {
        // if payload is too long, websocket will disconnect
        // therefore data will be sent in the chunks of max 200 streams
        int chunkSize = 200;
        for (int i = 0; i < streams.size(); i += chunkSize) {
            var subStreams = streams.subList(i, Math.min(i + chunkSize, streams.size()));
            subscribeToStreams(subStreams);
        }
    }

    private void subscribeToStreams(List<String> streams) throws Exception {
        WSSubscriptionRequest request = new WSSubscriptionRequest("SUBSCRIBE", streams, generateId());
        String message = objectMapper.writeValueAsString(request);
        socket.sendText(message);
    }

    private String generateId() {
        return "" + ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    }
}