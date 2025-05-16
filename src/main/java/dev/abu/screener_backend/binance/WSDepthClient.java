package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.OBMessageHandler;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static dev.abu.screener_backend.binance.OBManager.*;

@Getter
@Slf4j
public class WSDepthClient {

    @Getter
    private final String name;
    private final String wsUrl;
    private final boolean isSpot;
    private final OBMessageHandler messageHandler;
    private final Set<String> connectedSymbols;
    private final StandardWebSocketClient client;
    private WebSocketSession session;

    public WSDepthClient(String url, boolean isSpot) {
        this.name = isSpot ? "Spot" : "Futures";
        this.wsUrl = url;
        this.isSpot = isSpot;
        this.messageHandler = new OBMessageHandler(isSpot);
        this.connectedSymbols = ConcurrentHashMap.newKeySet();
        prepareReSyncMap(name);
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        container.setDefaultMaxTextMessageBufferSize(1024 * 1024); // 1MB buffer
        client = new StandardWebSocketClient(container);
    }

    public void startWebSocket() {
        var future = client.execute(new DepthHandler(), this.wsUrl);
        try {
            session = future.get();
        } catch (InterruptedException | ExecutionException e) {
            log.error("{} couldn't connect to websocket {}", name, e.getMessage());
            reconnect();
        }
    }

    public void reconnect() {
        log.info("{} Attempting reconnection", name);
        disconnect();

        var symbols = new HashSet<>(connectedSymbols);
        connectedSymbols.clear();

        int attempts = 0;
        while (attempts < 5 && (session == null || !session.isOpen())) {
            try {
                var future = client.execute(new DepthHandler(), this.wsUrl);
                session = future.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("{} Reconnection attempt {} failed: {}", name, attempts, e.getMessage());
                attempts++;
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }

        listenToSymbols(symbols);
    }

    public void disconnect() {
        if (session != null && session.isOpen()) {
            try {
                session.close();
                session = null;
            } catch (IOException e) {
                log.warn("{} failed to disconnect {}", name, e.getMessage());
            }
        }
    }

    public boolean isSymbolConnected(String symbol) {
        return connectedSymbols.contains(symbol);
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
        prepareOrderBooks(symbols, isSpot, name);

        // subscribing to binance streams
        for (int i = 0; i < symbols.size(); i += chunkSize) {
            var chunkOfSymbols = listOfSymbols.subList(i, Math.min(listOfSymbols.size(), i + chunkSize));
            var params = buildDepthParam(chunkOfSymbols);
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

            var params = buildDepthParam(chunkOfSymbols);
            if (!unsubscribe(params, generateId())) return;
        }

        // dropping related order book objects to free memory.
        dropOrderBooks(symbols, isSpot);
    }

    public boolean subscribe(Collection<String> params, String id) {
        try {
            var request = new BinanceSubscriptionRequest("SUBSCRIBE", params, id);
            String message = new ObjectMapper().writeValueAsString(request);
            return sendMessage(message);
        } catch (Exception e) {
            log.warn("{} couldn't subscribe to streams {}", name, e.getMessage());
            return false;
        }
    }

    public boolean unsubscribe(Collection<String> params, String id) {
        try {
            var request = new BinanceSubscriptionRequest("UNSUBSCRIBE", params, id);
            String message = new ObjectMapper().writeValueAsString(request);
            return sendMessage(message);
        } catch (Exception e) {
            log.warn("{} couldn't unsubscribe from streams {}", name, e.getMessage());
            return false;
        }
    }

    public boolean sendMessage(String message) {
        if (session == null || !session.isOpen()) {
            log.warn("{} cannot send message - no active WebSocket session", name);
            return false;
        }

        try {
            session.sendMessage(new TextMessage(message));
            return true;
        } catch (Exception e) {
            log.warn("{} couldn't send message to server - {}", name, e.getMessage());
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

    private Set<String> buildDepthParam(Collection<String> symbols) {
        Set<String> params = new HashSet<>();
        String prefix = isSpot ? "@depth" : "@depth@500ms";
        symbols.forEach(symbol -> params.add(symbol + prefix));
        return params;
    }

    private String generateId() {
        return "" + ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    }

    private Set<String> getUnconnectedSymbols(Set<String> allSymbols) {
        Set<String> unconnectedSymbols = new HashSet<>(allSymbols);
        unconnectedSymbols.removeAll(connectedSymbols);
        return unconnectedSymbols;
    }

    private Set<String> getDeletedSymbols(Set<String> allSymbols) {
        Set<String> deletedSymbols = new HashSet<>(connectedSymbols);
        deletedSymbols.removeAll(allSymbols);
        return deletedSymbols;
    }

    private class DepthHandler extends TextWebSocketHandler {
        @Override
        protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
            messageHandler.take(message);
        }

        @Override
        public void afterConnectionEstablished(@NonNull WebSocketSession session) {
            log.info("{} websocket connection established with uri {}", name, wsUrl);
        }

        @Override
        public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
            log.error("Transport error: {}", exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
            log.info("{} Disconnected from websocket with code {} and reason \"{}\"", name, status.getCode(), status.getReason());
            if (status.getReason() != null || status.getCode() == 1006) {
                reconnect();
            }
        }
    }
}