package dev.abu.screener_backend.analysis;

import dev.abu.screener_backend.binance.OBService;
import dev.abu.screener_backend.binance.depth.DepthUpdate;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    private final ConcurrentLinkedQueue<DepthUpdate> queue;
    private final boolean isSpot;
    private final OBService obService;

    public OBMessageHandler(boolean isSpot, OBService obService) {
        this.queue = new ConcurrentLinkedQueue<>();
        this.isSpot = isSpot;
        this.obService = obService;
        ScheduledExecutorService execService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName((isSpot ? "spot" : "fut") + "-msg-processor");
            return t;
        });
        execService.scheduleAtFixedRate(this::processor, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    public synchronized void take(DepthUpdate depthUpdate) {
        if (queue.size() > QUEUE_CAPACITY) queue.clear();
        queue.add(depthUpdate);
    }

    private void processor() {
        if (queue.isEmpty()) return;

        long start = System.nanoTime();

        Set<String> ineligibleSet = new HashSet<>();
        int count = 0;
        for (DepthUpdate depthUpdate : queue) {
            handleMessage(depthUpdate, ineligibleSet);
            count++;
        }

        long duration = (System.nanoTime() - start) / 1_000_000;
        if (duration > 2000) log.info("Finished processing {} events in {}ms", count, duration);
    }

    private void handleMessage(DepthUpdate depthUpdate, Set<String> ineligibleSet) {
        try {
            String eventType = depthUpdate.getEventType();
            if (eventType == null || !eventType.equals("depthUpdate")) {
                queue.remove(depthUpdate);
                return;
            }

            String symbol = depthUpdate.getSymbol();
            if (symbol == null) {
                queue.remove(depthUpdate);
                return;
            }

            String marketSymbol = symbol + (isSpot ? "" : FUT_SIGN);

            if (ineligibleSet.contains(marketSymbol)) return;
            OrderBook orderBook = obService.getOrderBook(marketSymbol);

            if (orderBook == null) {
                queue.remove(depthUpdate);

            } else if (orderBook.isTaskScheduled()) {
                ineligibleSet.add(symbol);

            } else if (orderBook.isScheduleNeeded() && obService.getNumOfScheduledTasks(isSpot) > SCHEDULE_THRESHOLD) {
                queue.remove(depthUpdate);

            } else {
                orderBook.process(depthUpdate);
                queue.remove(depthUpdate);
                analyzeEventCount();
            }

        } catch (Exception e) {
            log.error("Failed to read json data - {}", depthUpdate, e);
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