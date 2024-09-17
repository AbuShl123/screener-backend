package dev.abu.screener_backend.entity;

import dev.abu.screener_backend.binance.Ticker;
import dev.abu.screener_backend.binance.rest.BinanceOrderBookClient;
import dev.abu.screener_backend.handlers.OrderBookDataAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class ScreenerUser {

    private WebSocketSession session;
    private OrderBookDataAnalyzer analyzer;
    private BinanceOrderBookClient binance;
    private boolean connectionIsOn = true;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ScreenerUser(WebSocketSession session) {
        var symbol = getSymbol(session);
        if (symbol == null) return;
        var params = getQueryParams(session);
        var priceSpan = params.get("priceSpan");
        this.session = session;
        this.analyzer = new OrderBookDataAnalyzer(symbol, priceSpan);
        this.binance = new BinanceOrderBookClient(symbol);
        executorService.submit(this::startConnection);
    }

    public String getId() {
        return session.getId();
    }

    public void closeConnection() throws IOException {
        connectionIsOn = false;
        executorService.shutdown();
        this.session.close();
    }

    private void startConnection() {
        while (connectionIsOn) {
            String data = binance.getData();
            boolean isUpdated = analyzer.processData(data);
            if (isUpdated) sendUpdate();
        }
    }

    private void sendUpdate() {
        try {
            session.sendMessage(new TextMessage(analyzer.getJsonOrderBookData()));
        } catch (IOException e) {
            log.error("Error while sending order book data: {}", e.getMessage(), e);
        }
    }

    private synchronized static Ticker getSymbol(WebSocketSession session) {
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

    private synchronized static Map<String, String> getQueryParams(WebSocketSession session) {
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
}
