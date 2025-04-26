package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.Trade;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.abu.screener_backend.binance.OBManager.decrementReSyncCount;
import static dev.abu.screener_backend.binance.OBManager.incrementReSyncCount;
import static dev.abu.screener_backend.binance.DepthClient.getInitialSnapshot;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
public class OrderBook {

    private static final ExecutorService execService = Executors.newSingleThreadExecutor();

    private final ObjectMapper mapper = new ObjectMapper();
    private final String websocketName;
    @Getter private final String marketSymbol;
    private final boolean isSpot;
    private final OrderBookStream analyzer;
    @Getter private long lastUpdateId;
    private long lastReSyncTime;
    @Getter private boolean isReSync = true;
    private boolean isInitialEvent = false;
    @Getter private boolean isTaskScheduled = false;

    public OrderBook(String marketSymbol, boolean isSpot, String websocketName) {
        this.marketSymbol = marketSymbol;
        this.isSpot = isSpot;
        this.analyzer = OrderBookStream.createInstance(marketSymbol);
        this.websocketName = websocketName;
    }

    public TreeSet<Trade> getBids() {
        return analyzer.getBids();
    }

    public TreeSet<Trade> getAsks() {
        return analyzer.getAsks();
    }

    public Trade getMaxTrade(boolean isAsk){
        return analyzer.getMaxTrade(isAsk);
    }

    public boolean isScheduleNeeded() {
        return !isTaskScheduled && isReSync;
    }

    public void process(JsonNode root) {
        if (lastReSyncTime > 0 && System.currentTimeMillis() - lastReSyncTime >= 2 * 60 * 60 * 1000) {
            startReSync();
        }

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
        execService.submit(() -> {
            startProcessing(root);
            isTaskScheduled = false;
        });
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
            lastReSyncTime = System.currentTimeMillis();
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
        analyzer.reset();
        lastReSyncTime = 0;
        log.info("{} Initiating re-sync for {}", websocketName, marketSymbol);
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
        analyzer.analyze(root, asks, bids);
    }
}