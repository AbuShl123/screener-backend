package dev.abu.screener_backend.binance.ws;

import dev.abu.screener_backend.binance.OrderBook;
import dev.abu.screener_backend.binance.OBService;
import dev.abu.screener_backend.binance.entities.DepthEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static dev.abu.screener_backend.binance.dt.AsyncOBScheduler.getNumOfScheduledTasks;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Component
@Slf4j
public class DepthEventConsumer {

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

    private final ArrayBlockingQueue<DepthEvent> queue;
    private final Queue<DepthEvent> internalQueue;
    private final OBService obService;

    public DepthEventConsumer(OBService obService) {
        this.obService = obService;
        this.queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
        this.internalQueue = new ArrayDeque<>(QUEUE_CAPACITY);
        ScheduledExecutorService execService = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("depth-consumer");
            return t;
        });
        execService.scheduleAtFixedRate(this::consumer, 100L, 100L, TimeUnit.MILLISECONDS);
    }

    /**
     * Producer method that is called at a rate of max 864 messages per second.
     * @param depthEvent update from a websocket.
     */
    public void accept(DepthEvent depthEvent) {
        if (!queue.offer(depthEvent)) {
            log.warn("depth queue is full!");
        }
    }

    /**
     * Consumer function called every 100ms.
     */
    private void consumer() {
        List<DepthEvent> batch = new ArrayList<>();
        queue.drainTo(batch);
        if (batch.isEmpty()) return;
        internalQueue.addAll(batch);
        processInternalQueue();
    }

    /**
     * Method called by a consumer thread from a consumer() method.
     * Iterates through an internalQueue and processes depth events.
     */
    private void processInternalQueue() {
        Iterator<DepthEvent> it = internalQueue.iterator();
        Set<String> ineligibleSet = new HashSet<>();

        while (it.hasNext()) {
            DepthEvent update = it.next();
            boolean processed = handleMessage(update, ineligibleSet);
            if (processed) {
                it.remove();
            }
        }
    }

    /**
     * Method called by a consumer thread from a consumer() -> processInternalQueue() method.
     * @param depthEvent   POJO object representing a deserialized depth update from a websocket
     * @param ineligibleSet a set of symbols that should be preserved for a next processing.
     * @return true if this depthUpdate event should be removed, false otherwise.
     */
    private boolean handleMessage(DepthEvent depthEvent, Set<String> ineligibleSet) {
        try {
            String eventType = depthEvent.getEventType();
            if (eventType == null || !eventType.equals("depthUpdate")) {
                return true;
            }

            String symbol = depthEvent.getSymbol();
            if (symbol == null) {
                return true;
            }

            boolean isSpot = depthEvent.getLastUpdateId() == null;
            String marketSymbol = symbol + (isSpot ? "" : FUT_SIGN);

            if (ineligibleSet.contains(marketSymbol)) return false;
            OrderBook orderBook = obService.getOrderBook(marketSymbol);

            if (orderBook == null) {
                return true;

            } else if (orderBook.isTaskScheduled()) {
                ineligibleSet.add(symbol);

            } else if (orderBook.isScheduleNeeded() && getNumOfScheduledTasks(isSpot) > SCHEDULE_THRESHOLD) {
                return true;

            } else {
                orderBook.process(depthEvent);
                return true;
            }
        } catch (Exception e) {
            log.error("Failed to read json data - {}", depthEvent, e);
        }
        return false;
    }
}
