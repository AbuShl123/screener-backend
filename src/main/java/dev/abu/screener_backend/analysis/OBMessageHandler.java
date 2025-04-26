package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
    private static long lastCountUpdate = System.currentTimeMillis();
    private static int totalEventCount = 0;

    private final ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean isSpot;

    public OBMessageHandler(boolean isSpot) {
        this.isSpot = isSpot;
        ScheduledExecutorService execService = Executors.newSingleThreadScheduledExecutor();
        execService.scheduleAtFixedRate(this::processor, 100L, 100L, TimeUnit.MILLISECONDS);
    }

    public void take(WebSocketMessage<?> message) {
        if (!(message instanceof TextMessage)) return;

        if (queue.size() > QUEUE_CAPACITY) return;

        // TODO: Optimize this by checking how many tasks are scheduled in the order book.
        queue.add(message.getPayload().toString());

        if (queue.size() % 5000 == 0) {
            log.info("{} tasks scheduled", OrderBook.getNumOfScheduledTasks());
            log.info("{} messages are buffered", queue.size());
        }
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
            JsonNode data = root.get("data");
            JsonNode symbolNode = data.get("s");
            if (symbolNode == null) return;
            String symbol = symbolNode.asText().toLowerCase();
            var marketSymbol = symbol + (isSpot ? "" : FUT_SIGN);

            if (ineligibleSet.contains(marketSymbol)) return;
            OrderBook orderBook = getOrderBook(marketSymbol);

            if (orderBook == null) {
                queue.remove(message);
            }

            else if (orderBook.isTaskScheduled()) {
                ineligibleSet.add(symbol);
            }

            else if (orderBook.isScheduleNeeded() && OrderBook.getNumOfScheduledTasks() > 50) {
                queue.remove(message);
            }

            else {
                orderBook.process(data);
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

    /**
     * Prints the content of the queue as well as their eligibility status.
     * Used for debugging, slows down the execution.
     */
    private void enumerateQueue() {
        List<String> enumeratedQueue = new ArrayList<>();
        for (String message : queue) {
            try {
                JsonNode root = mapper.readTree(message);
                JsonNode data = root.get("data");
                JsonNode symbolNode = data.get("s");
                if (symbolNode == null) continue;
                String symbol = symbolNode.asText().toLowerCase();
                var marketSymbol = symbol + (isSpot ? "" : FUT_SIGN);
                enumeratedQueue.add(marketSymbol + "=" + getOrderBook(marketSymbol).isTaskScheduled());
            } catch (Exception e) {
                log.warn("Failure while enumeration - {}", message, e);
            }
        }
        System.out.println(enumeratedQueue);
    }

    public void printQueue() {
        for (String msg : queue) {
            log.info(msg.substring(msg.indexOf("\"s\""), msg.indexOf("\"b\"")));
        }
    }

    /**
     * Runs garbage collector and prints the memory usage of the JVM.
     */
    private void printMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();

        // Run garbage collector to get a more accurate picture
        runtime.gc();

        long totalMemory = runtime.totalMemory();     // total memory in JVM
        long freeMemory = runtime.freeMemory();       // free memory in JVM
        long usedMemory = totalMemory - freeMemory;   // memory actually used

        System.out.println("Total Memory: " + totalMemory / (1024 * 1024) + " MB");
        System.out.println("Free Memory: " + freeMemory / (1024 * 1024) + " MB");
        System.out.println("Used Memory: " + usedMemory / (1024 * 1024) + " MB");
    }
}