package dev.abu.screener_backend.handlers;

import dev.abu.screener_backend.binance.Ticker;
import dev.abu.screener_backend.binance.rest.BinanceClients;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;

@Component
@Slf4j
public class WSOrderBookHandler extends TextWebSocketHandler {

    /** Storing all WSOrderBook sessions */
    private static final Map<Ticker, Set<WebSocketSession>> sessions;
    /** Storing all analyzers - each session has its own analyzer */
    private static final Map<WebSocketSession, OrderBookDataAnalyzer> analyzers;

    static {
        sessions = new HashMap<>();
        analyzers = new HashMap<>();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        addSession(session);
        log.info("New Websocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session, getSymbol(session));
        log.info("Websocket connection closed: {}", session.getId());
    }

    private void addSession(WebSocketSession session) {
        var symbol = getSymbol(session);
        if (symbol == null) {return;}
        var params = getQueryParams(session);
        var dataSize = params.get("dataSize");
        var priceSpan = params.get("priceSpan");
        sessions.putIfAbsent(symbol, new HashSet<>());
        sessions.get(symbol).add(session);
        analyzers.putIfAbsent(session, new OrderBookDataAnalyzer(symbol, dataSize, priceSpan));
        BinanceClients.addBinanceOrderBookClient(symbol);
    }

    private Map<String, String> getQueryParams(WebSocketSession session) {
        Map<String, String> queryParams = new HashMap<>();
        String query = Objects.requireNonNull(session.getUri()).getQuery();

        if (query == null) return queryParams;

        String[] pairs = query.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length > 1) {
                queryParams.put(keyValue[0], keyValue[1]);
            }
        }

        return queryParams;
    }

    private void removeSession(WebSocketSession session, Ticker symbol) {
        var set = sessions.get(symbol);

        for (WebSocketSession webSocketSession : set) {
            if (webSocketSession.equals(session)) {
                set.remove(webSocketSession);
                break;
            }
        }

        try {
            session.close();
        } catch (IOException e) {
            log.error("Error closing websocket session", e);
        }

        if (set.isEmpty()) {
            // TODO: close binance session if the set is empty
        }
    }

    public static void broadCastOrderBookData(String orderBookData, Ticker symbol) {
        for (WebSocketSession session : sessions.get(symbol)) {
            var analyzer = analyzers.get(session);
            var processedData = analyzer.processData(orderBookData);
            try {
                if (session.isOpen()) {
                    session.sendMessage(new TextMessage(processedData));
                }
            } catch (Exception e) {
                log.error("Error sending order book data: ", e);
            }
        }
    }

    private Ticker getSymbol(WebSocketSession session) {
        String uri = Objects.requireNonNull(session.getUri()).toString();
        int endIndex = uri.contains("?") ? uri.indexOf('?') : uri.length();
        String symbol = uri.substring(uri.lastIndexOf('/') + 1, endIndex);
        try {
            return Ticker.valueOf(symbol.toUpperCase());
        } catch (Exception e) {
            log.error("Non-existent ticker: {}", symbol);
            return null;
        }
    }
}