package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.OBMessageHandler;
import jakarta.websocket.ContainerProvider;
import jakarta.websocket.WebSocketContainer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static dev.abu.screener_backend.binance.BinanceService.waitFor;
import static dev.abu.screener_backend.binance.OBManager.*;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Getter
@Slf4j
public class WSDepthClient {

    @Getter
    private final String name;
    private final String wsUrl;
    private final boolean isSpot;
    private final OBMessageHandler messageHandler;
    private final Set<String> connectedSymbols;
    private WebSocketSession session;
    private StandardWebSocketClient client;
    private boolean isConnected = false;

    public WSDepthClient(String url, boolean isSpot) {
        this.name = isSpot ? "Spot" : "Futures";
        this.wsUrl = url;
        this.isSpot = isSpot;
        this.messageHandler = new OBMessageHandler(isSpot);
        this.connectedSymbols = new HashSet<>();
        prepareReSyncMap(name);
    }

    public void startWebSocket() {
        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        // set max buffer size: 5MB
        container.setDefaultMaxTextMessageBufferSize(5 * 1024 * 1024);
        client = new StandardWebSocketClient(container);
        client.execute(new DepthHandler(), this.wsUrl);
        while (!isConnected) waitFor(100);
    }

    public void reconnect() {
        log.info("{} Attempting reconnection", name);
        connectedSymbols.clear();
        client.execute(new DepthHandler(), this.wsUrl);
    }

    public boolean isSymbolConnected(String symbol) {
        return connectedSymbols.contains(symbol);
    }

    public void subscribeToAllExistingSymbols(Set<String> symbols) {
        var newSymbols = getNewSymbols(symbols);
        var deletedSymbols = getDeletedSymbols(symbols);

        if (!newSymbols.isEmpty()) {
            var params = buildDepthParam(newSymbols);
            prepareOrderBooks(newSymbols, isSpot, name);
            subscribe(params, generateId());
            connectedSymbols.addAll(newSymbols);
        }

        if (!deletedSymbols.isEmpty()) {
            var params = buildDepthParam(deletedSymbols);
            connectedSymbols.removeAll(deletedSymbols);
            unsubscribe(params, generateId());
            dropOrderBooks(deletedSymbols, isSpot);
        }
    }

    public void subscribe(Collection<String> params, String id) {
        var request = new BinanceSubscriptionRequest("SUBSCRIBE", params, id);
        try {
            String message = new ObjectMapper().writeValueAsString(request);
            sendMessage(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void unsubscribe(Collection<String> params, String id) {
        var request = new BinanceSubscriptionRequest("UNSUBSCRIBE", params, id);
        try {
            String message = new ObjectMapper().writeValueAsString(request);
            sendMessage(message);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void sendMessage(String message) throws IOException {
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        } else {
            log.warn("{} cannot send message - no active WebSocket session", name);
            throw new IllegalStateException("{} webSocket session is not active");
        }
    }

    private Set<String> buildDepthParam(Collection<String> symbols) {
        Set<String> params = new HashSet<>();
        String prefix = isSpot ? "@depth" : "@depth@500ms";
        symbols.forEach(symbol -> params.add(symbol + prefix));
        return params;
    }

    private Set<String> getNewSymbols(Set<String> allSymbols) {
        return allSymbols.stream()
                .filter(symbol -> !connectedSymbols.contains(symbol))
                .collect(Collectors.toSet());
    }

    private Set<String> getDeletedSymbols(Set<String> allSymbols) {
        return connectedSymbols.stream()
                .filter(symbol -> !allSymbols.contains(symbol.replace(FUT_SIGN, "")))
                .collect(Collectors.toSet());
    }

    private String generateId() {
        return "" + ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
    }

    private class DepthHandler extends TextWebSocketHandler {

        @Override
        protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
            messageHandler.take(message);
        }

        @Override
        public void afterConnectionEstablished(@NonNull WebSocketSession session) {
            isConnected = true;
            WSDepthClient.this.session = session;
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