package dev.abu.screener_backend.binance.depth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.OBService;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public abstract class WSDepthClient {

    protected final StandardWebSocketClient client;
    protected final String url;
    protected final String name;
    protected final boolean isSpot;
    private final OBService obService;
    protected WebSocketSession session;
    protected WSDepthHandler wsDepthHandler;
    protected final Set<String> connectedSymbols;
    private final Object reconnectLock = new Object();
    CompletableFuture<WebSocketSession> currentConnectionFuture;

    public WSDepthClient(
            String url,
            String name,
            boolean isSpot,
            OBService obService
    ) {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024); // 1MB buffer
        this.client = new StandardWebSocketClient(container);
        this.isSpot = isSpot;
        this.obService = obService;
        this.connectedSymbols = ConcurrentHashMap.newKeySet();
        this.url = url;
        this.name = name;
        OBService.prepareReSyncMap(name);
    }

    public void startWebSocket() {
        currentConnectionFuture = client.execute(wsDepthHandler, url);
        try {
            session = currentConnectionFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("{} couldn't connect to websocket {}", name, e.getMessage());
            reconnect();
        }
    }

    public void reconnect() {
        synchronized (reconnectLock) {
            log.info("{} Attempting reconnection", name);

            var symbols = new HashSet<>(connectedSymbols);
            connectedSymbols.clear();
            int attempts = 0;

            do {
                try {
                    disconnect(currentConnectionFuture);
                    currentConnectionFuture = client.execute(wsDepthHandler, url);
                    session = currentConnectionFuture.get(3, TimeUnit.MINUTES);
                    log.info("{} successfully reconnected", name);
                } catch (Exception e) {
                    log.error("{} Reconnection attempt {} failed: {}", name, attempts, e.getMessage());
                    attempts++;
                    try {
                        Thread.sleep(2000L);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                    }
                }
            } while (attempts < 5 && (session == null || !session.isOpen()));

            listenToSymbols(symbols);
        }
    }

    public void disconnect(CompletableFuture<WebSocketSession> currentConnectFuture) {
        if (currentConnectFuture != null) {
            currentConnectFuture.cancel(true);
        }
        if (session != null && session.isOpen()) {
            try {
                session.close();
                session = null;
            } catch (IOException e) {
                log.warn("{} failed to disconnect: {}", name, e.getMessage());
            }
        }
    }

    public void listenToSymbols(Set<String> symbols) {
        var newSymbols = getUnconnectedSymbols(symbols);
        var deletedSymbols = getDeletedSymbols(symbols);

        if (!newSymbols.isEmpty()) {
            log.info("{} subscribing to {} symbols", name, newSymbols.size());
            subscribeToTickers(newSymbols);
        }

        if (!deletedSymbols.isEmpty()) {
            log.info("{} unsubscribing from {} symbols: {}", name, deletedSymbols.size(), deletedSymbols);
            unsubscribeFromTickers(deletedSymbols);
        }
    }

    private void subscribeToTickers(Collection<String> symbols) {
        // if payload is too long, websocket will disconnect
        // therefore data will be sent in chunks of max 315 symbols
        int chunkSize = 315;
        List<String> listOfSymbols = new ArrayList<>(symbols);

        // preparing OrderBook objects - always before opening connection
        obService.prepareOrderBooks(symbols, isSpot, name);

        // subscribing to binance streams
        for (int i = 0; i < symbols.size(); i += chunkSize) {
            var chunkOfSymbols = listOfSymbols.subList(i, Math.min(listOfSymbols.size(), i + chunkSize));
            var params = buildDepthParam(chunkOfSymbols, isSpot);
            if (!subscribe(params, generateId())) return;
        }

        // now adding symbols to 'connected' set
        connectedSymbols.addAll(symbols);
    }

    private void unsubscribeFromTickers(Collection<String> symbols) {
        // if payload is too long, websocket will disconnect
        // therefore data will be sent in chunks of max 315 symbols
        int chunkSize = 315;

        // removing symbols from 'connected' set
        connectedSymbols.removeAll(symbols);

        // subscribing to binance streams
        for (int i = 0; i < symbols.size(); i += chunkSize) {
            var chunkOfSymbols = symbols.stream()
                    .skip(i)
                    .limit(chunkSize)
                    .toList();

            var params = buildDepthParam(chunkOfSymbols, isSpot);
            if (!unsubscribe(params, generateId())) return;
        }

        // dropping related order book objects to free memory.
        obService.dropOrderBooks(symbols, isSpot);
    }

    public boolean subscribe(Collection<String> params, String id) {
        try {
            var request = new WSSubscriptionRequest("SUBSCRIBE", params, id);
            byte[] message = new ObjectMapper().writeValueAsBytes(request);
            return sendMessage(message);
        } catch (Exception e) {
            log.warn("{} couldn't subscribe to streams {}", name, e.getMessage());
            return false;
        }
    }

    public boolean unsubscribe(Collection<String> params, String id) {
        try {
            var request = new WSSubscriptionRequest("UNSUBSCRIBE", params, id);
            byte[] message = new ObjectMapper().writeValueAsBytes(request);
            return sendMessage(message);
        } catch (Exception e) {
            log.warn("{} couldn't unsubscribe from streams {}", name, e.getMessage());
            return false;
        }
    }

    public boolean sendMessage(byte[] message) {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new TextMessage(message));
                return true;
            } catch (Exception e) {
                log.warn("{} couldn't send message to server - {}", name, e.getMessage());
                return false;
            }
        } else {
            log.warn("{} cannot send message - no active WebSocket session", name);
            return false;
        }
    }

    public void sendPongMessage() {
        if (session != null && session.isOpen()) {
            try {
                session.sendMessage(new PongMessage());
            } catch (IOException e) {
                log.error("{} failed to send pong message: {}", name, e.getMessage());
            }
        } else {
            log.warn("{} cannot send pong message - no active WebSocket session", name);
        }
    }

    protected Set<String> buildDepthParam(Collection<String> symbols, boolean isSpot) {
        Set<String> params = new HashSet<>();
        String prefix = isSpot ? "@depth" : "@depth@500ms";
        symbols.forEach(symbol -> params.add(symbol + prefix));
        return params;
    }

    protected String generateId() {
        return "" + ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    }

    protected Set<String> getUnconnectedSymbols(Set<String> allSymbols) {
        Set<String> unconnectedSymbols = new HashSet<>(allSymbols);
        unconnectedSymbols.removeAll(connectedSymbols);
        return unconnectedSymbols;
    }

    protected Set<String> getDeletedSymbols(Set<String> allSymbols) {
        Set<String> deletedSymbols = new HashSet<>(connectedSymbols);
        deletedSymbols.removeAll(allSymbols);
        return deletedSymbols;
    }
}
