package dev.abu.screener_backend.analysis;

import dev.abu.screener_backend.binance.OBService;
import dev.abu.screener_backend.binance.depth.DepthUpdate;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
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

    private final LinkedBlockingQueue<DepthUpdate> queue;
    private final Queue<DepthUpdate> internalQueue;
    private final boolean isSpot;
    private final OBService obService;

    public OBMessageHandler(boolean isSpot, OBService obService) {
        this.queue = new LinkedBlockingQueue<>(QUEUE_CAPACITY);
        this.internalQueue = new ArrayDeque<>(QUEUE_CAPACITY);
        this.isSpot = isSpot;
        this.obService = obService;
        ScheduledExecutorService execService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName((isSpot ? "s" : "f") + "-msg-processor");
            return t;
        });
        execService.scheduleAtFixedRate(this::processor, 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    /**
     * Producer method that is called at a rate of max 864 messages per second.
     *
     * @param depthUpdate POJO object representing a deserialized depth update from a websocket.
     */
    public void take(DepthUpdate depthUpdate) {
        boolean success = queue.offer(depthUpdate);
        if (!success) {
            log.warn("isSpot={} queue is full!", isSpot);
            queue.clear();
        }
    }

    /**
     * Consumer function called every second by a scheduled thread.
     */
    private void processor() {
        List<DepthUpdate> batch = new ArrayList<>();
        queue.drainTo(batch);
        if (!batch.isEmpty()) {
            internalQueue.addAll(batch);
        }
        processInternalQueue();
    }

    /**
     * Method called by a consumer thread from a processor() method.
     * Iterates through an internalQueue and processes depth events.
     */
    private void processInternalQueue() {
        Iterator<DepthUpdate> it = internalQueue.iterator();
        Set<String> ineligibleSet = new HashSet<>();

        while (it.hasNext()) {
            DepthUpdate update = it.next();
            boolean processed = handleMessage(update, ineligibleSet);
            if (processed) {
                it.remove();
            }
        }
    }

    /**
     * Method called by a consumer thread from a processor() -> processInternalQueue() method.
     *
     * @param depthUpdate   POJO object representing a deserialized depth update from a websocket
     * @param ineligibleSet a set of symbols that should be preserved for a next processing.
     * @return true if this depthUpdate event should be removed, false otherwise.
     */
    private boolean handleMessage(DepthUpdate depthUpdate, Set<String> ineligibleSet) {
        try {
            String eventType = depthUpdate.getEventType();
            if (eventType == null || !eventType.equals("depthUpdate")) {
                return true;
            }

            String symbol = depthUpdate.getSymbol();
            if (symbol == null) {
                return true;
            }

            String marketSymbol = symbol + (isSpot ? "" : FUT_SIGN);

            if (ineligibleSet.contains(marketSymbol)) return false;
            OrderBook orderBook = obService.getOrderBook(marketSymbol);

            if (orderBook == null) {
                return true;
            } else if (orderBook.isTaskScheduled()) {
                ineligibleSet.add(symbol);
            } else if (orderBook.isScheduleNeeded() && obService.getNumOfScheduledTasks(isSpot) > SCHEDULE_THRESHOLD) {
                return true;
            } else {
                orderBook.process(depthUpdate);
                analyzeEventCount();
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to read json data - {}", depthUpdate, e);
        }
        return false;
    }

    /**
     * Event count analyzer method - very crucial to understand that the number
     * of events processed by the application is at the healthy level.
     * Futures and Spot push updates at a rate of ~1200 messages per second (worst scenario),
     * therefore the usual number of processed events should be ~72000 per minute.
     * In production, it's usually 60K events per minute (as of June 14, 2025).
     */
    private static synchronized void analyzeEventCount() {
        totalEventCount++;
        if (System.currentTimeMillis() - lastCountUpdate > 60_000) {
            log.info("Total {} events processed in 1 minute", totalEventCount);
            totalEventCount = 0;
            lastCountUpdate = System.currentTimeMillis();
        }
    }
}