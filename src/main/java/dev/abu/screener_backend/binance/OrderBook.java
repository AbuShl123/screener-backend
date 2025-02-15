package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.OrderBookStream;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.abu.screener_backend.binance.DepthClient.getInitialSnapshot;

@Slf4j
public class OrderBook {

    private static final Map<String, Integer> reSyncCountMap = new ConcurrentHashMap<>();

    private final String websocketName;
    private final ObjectMapper mapper = new ObjectMapper();
    private final String symbol;
    private final OrderBookStream analyzer;
    private long lastUpdateId;
    private boolean reSync = true;
    private boolean initialEvent = false;

    public OrderBook(String symbol, String websocketName) {
        this.symbol = symbol;
        this.analyzer = OrderBookStream.createInstance(symbol);
        this.websocketName = websocketName;
        reSyncCountMap.putIfAbsent(websocketName, 0);
    }

    public static int reSyncCount(String websocketName) {
        return reSyncCountMap.get(websocketName);
    }

    private int reSyncCount() {
        return reSyncCountMap.getOrDefault(websocketName, 0);
    }

    public void process(JsonNode root) {
        long U = root.get("U").asLong();
        long u = root.get("u").asLong();

        if (reSync) {
            processInitialSnapshot(root, U);
        }

        if (initialEvent) {
            if (lastUpdateId >= U && lastUpdateId <= u) {
                lastUpdateId = u;
                initialEvent = false;
                analyzeData(root, false);
                incrementReSyncCount();
                if (reSyncCount() == 10) log.info("Finished re-sync for {}", websocketName);
            }
            return;
        }

        if (U <= lastUpdateId + 1 && lastUpdateId < u) {
            lastUpdateId = u;
            analyzeData(root, false);
        } else {
            startReSync();
        }
    }

    private void startReSync() {
        reSync = true;
        analyzer.reset();
        decrementReSyncCount();
        log.info("{} Initiating re-sync for {}", websocketName, symbol);
    }

    private void processInitialSnapshot(JsonNode root, long U) {
        try {

            lastUpdateId = 0;
            JsonNode snapshot;

            do {
                String raw = getInitialSnapshot(symbol, true);
                snapshot = mapper.readTree(raw);
                lastUpdateId = snapshot.get("lastUpdateId").asLong();
            } while (lastUpdateId < U);

            reSync = false;
            initialEvent = true;
            analyzeData(root, true);

        } catch (IOException e) {
            log.error("{} Error processing snapshot", websocketName, e);
            reSync = true;
        }
    }

    private void analyzeData(JsonNode root, boolean isSnapshot) {
        JsonNode asks = isSnapshot ? root.get("asks") : root.get("a");
        JsonNode bids = isSnapshot ? root.get("bids") : root.get("b");
        analyzer.analyze(root, asks, bids);
    }

    private void incrementReSyncCount() {
        reSyncCountMap.put(websocketName, reSyncCountMap.get(websocketName) + 1);
    }

    private void decrementReSyncCount() {
        reSyncCountMap.put(websocketName, reSyncCountMap.get(websocketName) - 1);
    }
}