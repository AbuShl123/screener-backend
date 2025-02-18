package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static dev.abu.screener_backend.TasksRunner.decrementReSyncCount;
import static dev.abu.screener_backend.TasksRunner.incrementReSyncCount;
import static dev.abu.screener_backend.binance.DepthClient.getInitialSnapshot;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
public class OrderBook {
    private static final ExecutorService execService = Executors.newSingleThreadExecutor();

    private final ObjectMapper mapper = new ObjectMapper();
    private final Queue<JsonNode> messageBuffer = new ArrayDeque<>();
    private final String websocketName;
    private final String symbol;
    private final boolean isSpot;
    private final OrderBookStream analyzer;
    private long lastUpdateId;
    private long lastReSyncTime;
    private boolean isReSync = true;
    private boolean isInitialEvent = false;
    private boolean isTaskScheduled = false;

    public OrderBook(String symbol, boolean isSpot, String websocketName) {
        this.symbol = symbol;
        this.isSpot = isSpot;
        this.analyzer = OrderBookStream.createInstance(symbol + (isSpot ? "" : FUT_SIGN));
        this.websocketName = websocketName;
    }

    public void process(JsonNode root) {
        // every hour re-sync is needed.
        if (!isReSync && System.currentTimeMillis() - lastReSyncTime > 60 * 60 * 1000 ) {
            startReSync();
        }

        // if re-sync is needed and there is no any task that is queued for concurrent run,
        // then process this event concurrently to get the initial snapshot
        if (!isTaskScheduled && isReSync) {
            processEventConcurrently(root);
        }

        // if initial snapshot has already been processed (in which case no task is scheduled for concurrent run),
        // then process the events as usual
        else if (!isTaskScheduled) {
            processEvent(root);
        }

        // in any other case, the events will be buffered until the
        // concurrent task is finished
        else {
            bufferEvent(root);
            if (messageBuffer.size() % 100 == 0) log.info("{} {} is late by {} events", websocketName, symbol, messageBuffer.size());
        }
    }

    private void processEventConcurrently(JsonNode root) {
        isTaskScheduled = true;
        execService.submit(() -> {
            processWithInitialSnapshot(root);
            log.info("{} {} Thread Finished", websocketName, symbol);
            isTaskScheduled = false;
        });
    }

    private void processWithInitialSnapshot(JsonNode root) {
        long U = root.get("U").asLong();
        long u = root.get("u").asLong();
        processInitialSnapshot(root, U);
        processInitialEvent(root, U, u);
    }

    private void processEvent(JsonNode root) {
        if (bufferNotEmpty()) {
            bufferEvent(root);
            root = pollEvent();
        }

        long U = root.get("U").asLong();
        long u = root.get("u").asLong();

        if (isInitialEvent) {
            processInitialEvent(root, U, u);
        } else {
            lastUpdateId = u;
            analyzeData(root, false);
        }
    }

    private void processInitialEvent(JsonNode root, long U, long u) {
        lastUpdateId = u;
        analyzeData(root, false);
        isInitialEvent = false;
    }

    private void startReSync() {
        isReSync = true;
        decrementReSyncCount(websocketName);
        analyzer.reset();
        log.info("{} Initiating re-sync for {}", websocketName, symbol);
    }

    private void processInitialSnapshot(JsonNode root, long U) {
        String raw = null;

        try {
            lastUpdateId = 0;
            JsonNode snapshot;

            do {
                raw = getInitialSnapshot(symbol, isSpot);
                snapshot = mapper.readTree(raw);
                lastUpdateId = snapshot.get("lastUpdateId").asLong();
            } while (lastUpdateId < U);

            analyzeData(root, true);
            lastReSyncTime = System.currentTimeMillis();
            isReSync = false;
            isInitialEvent = true;
            incrementReSyncCount(websocketName);

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

    private void bufferEvent(JsonNode event) {
        messageBuffer.offer(event);
    }

    private JsonNode pollEvent() {
        return messageBuffer.poll();
    }

    private boolean bufferNotEmpty() {
        return !messageBuffer.isEmpty();
    }
}