package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

import static dev.abu.screener_backend.binance.DepthClient.getDepthSnapshot;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
public class LocalOrderBook {

    private static final Map<String, LocalOrderBook> map = new HashMap<>();
    private static final long AUTO_RE_SYNC_FREQUENCY = 60 * 60 * 1000;

    private final ObjectMapper mapper = new ObjectMapper();
    private final String symbol;
    private final boolean isSpot;
    private final OrderBookStream stream;

    private boolean reSync;
    private boolean initialEvent;
    private long lastUpdateID;
    private long lastReSyncTime;

    public static LocalOrderBook getInstance(final String symbol) {
        return map.get(symbol);
    }

    public static void createInstance(final String symbol, boolean isSpot) {
        map.put(symbol, new LocalOrderBook(symbol, isSpot));
    }

    LocalOrderBook(String symbol, boolean isSpot) {
        this.symbol = symbol;
        this.isSpot = isSpot;
        this.reSync = true;
        this.stream = OrderBookStream.getInstance(symbol);
    }

    public synchronized void process(String rawData) throws JsonProcessingException {
        JsonNode event = mapper.readTree(rawData).get("data");
        String eventType = event.get("e").asText();
        if (!eventType.equals("depthUpdate")) return;

        long U = event.get("U").asLong();
        long u = event.get("u").asLong();

        if (reSync) {
            sendSnapshot(U);
        }

        if (initialEvent && lastUpdateID > u) return;

        if (initialEvent) {
            stream.buffer(rawData);
            lastUpdateID = u;
            lastReSyncTime = System.currentTimeMillis();
            initialEvent = false;
            log.info("Initial event sent for {}", symbol);
            return;
        }

        if (System.currentTimeMillis() - lastReSyncTime >= AUTO_RE_SYNC_FREQUENCY) {
            reSyncOrderBook();
            return;
        }

        if (u < lastUpdateID) {
            return;
        }

        if (isSpot && U <= lastUpdateID + 1 && u > lastUpdateID) {
            stream.buffer(rawData);
            lastUpdateID = u;
            return;
        }

        if (!isSpot) {
            long pu = event.get("pu").asLong();
            if (pu == lastUpdateID) {
                stream.buffer(rawData);
                lastUpdateID = u;
                return;
            }
        }

        reSyncOrderBook();
    }

    public void reSyncOrderBook() {
        log.warn("Re-syncing order book for symbol {}", symbol);
        reSync = true;
    }

    public boolean isReSyncCompleted() {
        return !initialEvent && !reSync;
    }

    private void sendSnapshot(long U) throws JsonProcessingException {
        lastUpdateID = 0;
        String depthSnapshot;

        do {
            String ticker = symbol.replace(FUT_SIGN, "");
            depthSnapshot = getDepthSnapshot(ticker, isSpot);
            JsonNode snapshot = mapper.readTree(depthSnapshot);
            lastUpdateID = snapshot.get("lastUpdateId").asLong();
        } while (lastUpdateID < U);

        stream.clear();
        stream.buffer(depthSnapshot);
        reSync = false;
        initialEvent = true;
    }
}