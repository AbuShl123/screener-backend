package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.MessageBuffer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import java.util.HashMap;
import java.util.Map;

import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
public class OBMessageHandler {

    private static final Map<String, OrderBook> orderBooks = new HashMap<>();
    private static long lastCountUpdate = System.currentTimeMillis();
    private static int totalEventCount = 0;

    private final MessageBuffer<String> messageBuffer = new MessageBuffer<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String websocketName;
    private final boolean isSpot;

    public OBMessageHandler(String name, boolean isSpot, String... symbols) {
        this.websocketName = name;
        this.isSpot = isSpot;
        setAllOrderBooks(symbols);
        messageBuffer.startProcessing(this::handleMessage);
    }

    public void handleMessage(WebSocketMessage<?> message) {
        if (!(message instanceof TextMessage)) return;
        messageBuffer.buffer(message.getPayload().toString());
        if (messageBuffer.size() % 100 == 0)
            log.info("{} {} messages are buffered", websocketName, messageBuffer.size());
    }

    private synchronized void handleMessage(String message) {
        long start = System.nanoTime();
        var symbol = processMessage(message);
        analyzeEventCount();
        long duration = (System.nanoTime() - start) / 1_000_000;
        if (duration > 50) log.info("{} {} Processed event in {}ms", websocketName, symbol, duration);
    }

    private synchronized String processMessage(String message) {
        try {
            JsonNode root = mapper.readTree(message);
            JsonNode data = root.get("data");
            JsonNode symbolNode = data.get("s");
            if (symbolNode == null) return null;
            String symbol = symbolNode.asText().toLowerCase();
            var marketSymbol = symbol + (isSpot ? "" : FUT_SIGN);

            orderBooks.get(marketSymbol).process(data);
            return marketSymbol;
        } catch (Exception e) {
            log.error("{} Failed to read json data - {}", websocketName, message, e);
        }
        return null;
    }

    private static synchronized void analyzeEventCount() {
        totalEventCount++;
        if (System.currentTimeMillis() - lastCountUpdate > 60_000) {
            log.info("Total {} events processed in 1 minute", totalEventCount);
            totalEventCount = 0;
            lastCountUpdate = System.currentTimeMillis();
        }
    }

    private void setAllOrderBooks(String... symbols) {
        for (String symbol : symbols) {
            var marketSymbol = symbol + (isSpot ? "" : FUT_SIGN);
            var orderbook = new OrderBook(symbol, isSpot, websocketName);
            orderBooks.putIfAbsent(marketSymbol, orderbook);
        }
    }
}
