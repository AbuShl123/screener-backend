package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.analysis.OrderBook;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
public class OBManager {

    private static final ThreadPoolExecutor spotExecService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    private static final ThreadPoolExecutor futExecService = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());
    private static final Map<String, OrderBook> orderBooks = new HashMap<>();
    private static final Map<String, Set<String>> reSyncCountMap = new ConcurrentHashMap<>();

    private OBManager() {}

    public synchronized static int reSyncCount(String websocketName) {
        return reSyncCountMap.get(websocketName).size();
    }

    public synchronized static void incrementReSyncCount(String websocketName, String symbol) {
        reSyncCountMap.get(websocketName).add(symbol);
    }

    public synchronized static void decrementReSyncCount(String websocketName, String symbol) {
        reSyncCountMap.get(websocketName).remove(symbol);
    }

    public synchronized static void prepareReSyncMap(String websocketName) {
        reSyncCountMap.put(websocketName, new HashSet<>());
    }

    public synchronized static OrderBook getOrderBook(String marketSymbol) {
        return orderBooks.get(marketSymbol);
    }

    public synchronized static Collection<OrderBook> getAllOrderBooks() {
        return orderBooks.values();
    }

    public synchronized static long getNumOfScheduledTasks(boolean isSpot) {
        if (isSpot) return spotExecService.getQueue().size();
        return futExecService.getQueue().size();
    }

    public synchronized static void scheduleTask(Runnable task, boolean isSpot) {
        if (isSpot) spotExecService.submit(task);
        else futExecService.submit(task);
    }

    public synchronized static void printReSyncMap() {
        StringBuilder sb = new StringBuilder("re-sync map: {");
        for (var entry : reSyncCountMap.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue().size()).append(", ");
        }
        if (sb.toString().endsWith(", ")) sb.delete(sb.length() - 2, sb.length());
        sb.append("}");
        log.info(sb.toString());
    }

    public synchronized static void prepareOrderBooks(Collection<String> symbols, boolean isSpot, String websocketName) {
        for (String symbol : symbols) {
            String marketSymbol = isSpot ? symbol : symbol + FUT_SIGN;
            orderBooks.putIfAbsent(marketSymbol, new OrderBook(marketSymbol, isSpot, websocketName));
        }
    }

    public synchronized static void dropOrderBooks(Collection<String> symbols, boolean isSpot) {
        for (String symbol : symbols) {
            String marketSymbol = isSpot ? symbol : symbol + FUT_SIGN;
            orderBooks.remove(marketSymbol);
        }
    }
}
