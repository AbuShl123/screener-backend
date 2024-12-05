package dev.abu.screener_backend.entity;

import dev.abu.screener_backend.analysis.OrderBookDataAnalyzer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.abu.screener_backend.binance.BinanceOrderBookClient.getOrderBook;

@Slf4j
public class ScreenerUser {

    private final WebSocketSession session;
    private final Ticker ticker;
    private final OrderBookDataAnalyzer analyzer;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private boolean connectionIsOn = true;

    public ScreenerUser(WebSocketSession session, Ticker ticker, String priceSpan) {
        this.session = session;
        this.ticker = ticker;
        this.analyzer = new OrderBookDataAnalyzer(ticker, priceSpan);
        executorService.submit(this::startConnection);
    }

    public String getId() {
        return session.getId();
    }

    public void closeConnection() throws IOException {
        connectionIsOn = false;
        executorService.shutdown();
    }

    private void startConnection() {
        while (connectionIsOn) {
            String data = getOrderBook(ticker);
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
}
