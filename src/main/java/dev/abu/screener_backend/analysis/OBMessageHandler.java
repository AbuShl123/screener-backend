package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static dev.abu.screener_backend.binance.OBManager.getNumOfScheduledTasks;
import static dev.abu.screener_backend.binance.OBManager.getOrderBook;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
public class OBMessageHandler {

    /**
     * In average, one message weights 7000 bytes (0.007MB).
     * If the desired maximum size of the queue should be 210MB,
     * then the queue capacity is approx 210MB/0.007MB = 30,000.
     * <br> <br>
     * The maximum weight of the queue is <b>210MB</b> with the capacity of <b>30,000</b> messages,
     * where each message weights approximately <b>7KB</b>
     */
    private static final int QUEUE_CAPACITY = 30_000;
    private static final int SCHEDULE_THRESHOLD = 115;
    private static long lastCountUpdate = System.currentTimeMillis();
    private static int totalEventCount = 0;

    private final ConcurrentLinkedQueue<String> queue;
    private final ObjectMapper mapper;
    private final boolean isSpot;

    public OBMessageHandler(boolean isSpot) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.mapper = new ObjectMapper();
        this.isSpot = isSpot;
        ScheduledExecutorService execService = Executors.newSingleThreadScheduledExecutor();
        execService.scheduleAtFixedRate(this::processor, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    public synchronized void take(TextMessage message) {
        if (queue.size() > QUEUE_CAPACITY) queue.clear();
        queue.add(message.getPayload());
    }

    private void processor() {
        if (queue.isEmpty()) return;

        long start = System.nanoTime();

        Set<String> ineligibleSet = new HashSet<>();
        int count = 0;
        for (String message : queue) {
            handleMessage(message, ineligibleSet);
            count++;
        }

        long duration = (System.nanoTime() - start) / 1_000_000;
        if (duration > 1000) log.info("Finished processing {} events in {}ms", count, duration);
    }

    private void handleMessage(String message, Set<String> ineligibleSet) {
        try {

            JsonNode root = mapper.readTree(message);
            JsonNode eventType = root.get("e");
            if (eventType == null || !eventType.asText().equals("depthUpdate")) {
                queue.remove(message);
                return;
            }

            JsonNode symbolNode = root.get("s");
            if (symbolNode == null) {
                queue.remove(message);
                return;
            }

            String symbol = symbolNode.asText().toLowerCase();
            var marketSymbol = symbol + (isSpot ? "" : FUT_SIGN);

            if (ineligibleSet.contains(marketSymbol)) return;
            OrderBook orderBook = getOrderBook(marketSymbol);

            if (orderBook == null) {
                queue.remove(message);
            } else if (orderBook.isTaskScheduled()) {
                ineligibleSet.add(symbol);
            } else if (orderBook.isScheduleNeeded() && getNumOfScheduledTasks(isSpot) > SCHEDULE_THRESHOLD) {
                queue.remove(message);
            } else {
                orderBook.process(root);
                queue.remove(message);
                analyzeEventCount();
            }

        } catch (Exception e) {
            log.error("Failed to read json data - {}", message, e);
        }
    }

    private static synchronized void analyzeEventCount() {
        totalEventCount++;
        if (System.currentTimeMillis() - lastCountUpdate > 60_000) {
            log.info("Total {} events processed in 1 minute", totalEventCount);
            totalEventCount = 0;
            lastCountUpdate = System.currentTimeMillis();
        }
    }
}