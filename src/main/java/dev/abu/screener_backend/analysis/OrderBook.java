package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.websockets.SessionPool;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static dev.abu.screener_backend.binance.DepthClient.getInitialSnapshot;
import static dev.abu.screener_backend.binance.OBManager.*;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;
import static dev.abu.screener_backend.utils.EnvParams.MAX_INCLINE;
import static java.lang.Math.abs;
import static java.lang.Math.round;

@Slf4j
public class OrderBook {

    private final ObjectMapper mapper = new ObjectMapper();
    private final String websocketName;
    private final boolean isSpot;
    private boolean isInitialEvent = false;

    @Getter
    private final TradeList tradeList;
    @Getter
    private final String marketSymbol;
    @Getter
    private long lastUpdateId;
    @Getter
    private boolean isTaskScheduled = false;
    @Getter
    private boolean isReSync = true;

    public OrderBook(String marketSymbol, boolean isSpot, String websocketName, SessionPool sessionPool) {
        this.marketSymbol = marketSymbol;
        this.tradeList = new TradeList(marketSymbol, sessionPool);
        this.isSpot = isSpot;
        this.websocketName = websocketName;
    }

    public Trade getMaxTrade(boolean isAsk) {
        return tradeList.getMaxTrade(isAsk);
    }

    public boolean isScheduleNeeded() {
        return !isTaskScheduled && isReSync;
    }

    public void process(JsonNode root) {
        // if re-sync is needed and there is no task that is queued for concurrent run,
        // then process this event concurrently to get the initial snapshot
        if (isScheduleNeeded()) {
            processEventConcurrently(root);
        }

        // if initial snapshot has already been processed (in which case no task is scheduled for concurrent run),
        // then process the events as usual
        else if (!isTaskScheduled) {
            processEvent(root);
        }

        // in any other case, events will be ignored, so they should be kept in the buffer.
    }

    private void processEventConcurrently(JsonNode root) {
        isTaskScheduled = true;
        scheduleTask(() -> {
            startProcessing(root);
            isTaskScheduled = false;
        }, isSpot);
    }

    private void startProcessing(JsonNode root) {
        long U = root.get("U").asLong();
        long u = root.get("u").asLong();
        processInitialSnapshot(U);
        processInitialEvent(root, U, u);
    }

    private void processEvent(JsonNode root) {
        long U = root.get("U").asLong();
        long u = root.get("u").asLong();

        if (isInitialEvent) {
            processInitialEvent(root, U, u);
        } else if (isSpot) {
            processSpotEvent(root, U, u);
        } else {
            processFutEvent(root, u);
        }
    }

    private void processInitialEvent(JsonNode root, long U, long u) {
        if (lastUpdateId >= U && lastUpdateId <= u) {
            lastUpdateId = u;
            analyzeData(root, false);
            isInitialEvent = false;
            incrementReSyncCount(websocketName, marketSymbol);
        } else if (U > lastUpdateId) {
            startReSync();
        }
    }

    private void processSpotEvent(JsonNode root, long U, long u) {
        if (lastUpdateId + 1 >= U && lastUpdateId < u) {
            lastUpdateId = u;
            analyzeData(root, false);
        } else {
            startReSync();
        }
    }

    private void processFutEvent(JsonNode root, long u) {
        long pu = root.get("pu").asLong();
        if (lastUpdateId == pu) {
            lastUpdateId = u;
            analyzeData(root, false);
        } else {
            startReSync();
        }
    }

    private void startReSync() {
        isReSync = true;
        decrementReSyncCount(websocketName, marketSymbol);
        tradeList.clear();
    }

    private void processInitialSnapshot(long U) {
        String raw = null;

        try {
            lastUpdateId = 0;
            JsonNode snapshot;

            do {
                raw = getInitialSnapshot(marketSymbol.replace(FUT_SIGN, ""), isSpot);
                snapshot = mapper.readTree(raw);
                lastUpdateId = snapshot.get("lastUpdateId").asLong();
            } while (lastUpdateId < U);

            analyzeData(snapshot, true);
            isReSync = false;
            isInitialEvent = true;

        } catch (Exception e) {
            log.error("{} Error processing snapshot: {}", websocketName, raw, e);
            isReSync = true;
        }
    }

    private void analyzeData(JsonNode root, boolean isSnapshot) {
        JsonNode asks = isSnapshot ? root.get("asks") : root.get("a");
        JsonNode bids = isSnapshot ? root.get("bids") : root.get("b");
        analyze(root, asks, bids);
    }

    private void analyze(JsonNode root, JsonNode asksArray, JsonNode bidsArray) {
        long timestamp = getTimeStamp(root);
        traverseArray(asksArray, timestamp, true);
        traverseArray(bidsArray, timestamp, false);
    }

    private long getTimeStamp(JsonNode root) {
        JsonNode eField = root.get("E");
        if (eField == null) return System.currentTimeMillis();
        long timestamp = eField.asLong();
        return timestamp == 0 ? System.currentTimeMillis() : timestamp;
    }

    private void traverseArray(JsonNode array, long timestamp, boolean isAsk) {
        if (array == null || (array.isArray() && array.isEmpty())) {
            return;
        }
        for (JsonNode node : array) {
            var price = node.get(0).asDouble();
            var qty = node.get(1).asDouble();
            double distance = getDistance(price);
            if (distance <= MAX_INCLINE) {
                tradeList.addTrade(price, qty, distance, isAsk, timestamp);
            }
        }
    }

    private double getDistance(double price) {
        double marketPrice = TickerService.getPrice(marketSymbol);
        double distance = abs((price - marketPrice) / marketPrice * 100);
        return round(distance * 100.0) / 100.0;
    }
}