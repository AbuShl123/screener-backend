package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.OrderBook;
import dev.abu.screener_backend.websockets.SessionPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Service
@Slf4j
@RequiredArgsConstructor
public class OBService {
    private static final Map<String, Set<String>> reSyncCountMap = new ConcurrentHashMap<>();

    private static final ThreadPoolExecutor spotExecService = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread thread = new Thread(r);
                thread.setName("spot-async-scheduler");
                return thread;
            });

    private static final ThreadPoolExecutor futExecService = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread thread = new Thread(r);
                thread.setName("fut-async-scheduler");
                return thread;
            });

    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final SessionPool sessionPool;

    public static void incrementReSyncCount(String websocketName, String symbol) {
        reSyncCountMap.get(websocketName).add(symbol);
    }

    public static void decrementReSyncCount(String websocketName, String symbol) {
        reSyncCountMap.get(websocketName).remove(symbol);
    }

    public static void prepareReSyncMap(String websocketName) {
        reSyncCountMap.put(websocketName, new HashSet<>());
    }

    public static void printReSyncMap() {
        StringBuilder sb = new StringBuilder("re-sync map: {");
        for (var entry : reSyncCountMap.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue().size()).append(", ");
        }
        if (sb.toString().endsWith(", ")) sb.delete(sb.length() - 2, sb.length());
        sb.append("}");
        log.info(sb.toString());
    }

    public static void scheduleTask(Runnable task, boolean isSpot) {
        if (isSpot) spotExecService.submit(task);
        else futExecService.submit(task);
    }

    public synchronized OrderBook getOrderBook(String marketSymbol) {
        return orderBooks.get(marketSymbol);
    }

    public long getNumOfScheduledTasks(boolean isSpot) {
        if (isSpot) return spotExecService.getQueue().size();
        return futExecService.getQueue().size();
    }

    public void prepareOrderBooks(
            Collection<String> symbols,
            boolean isSpot,
            String websocketName
    ) {
        for (String symbol : symbols) {
            String marketSymbol = isSpot ? symbol : symbol + FUT_SIGN;
            orderBooks.putIfAbsent(marketSymbol, new OrderBook(marketSymbol, isSpot, websocketName, sessionPool, mapper));
        }
    }

    public void updateDistancesAndLevels() {
        for (OrderBook orderBook : orderBooks.values()) {
            orderBook.getTradeList().updateLevelsAndDistances(TickerService.getPrice(orderBook.getMarketSymbol()));
        }
    }

    public void dropOrderBooks(Collection<String> symbols, boolean isSpot) {
        for (String symbol : symbols) {
            String marketSymbol = isSpot ? symbol : symbol + FUT_SIGN;
            orderBooks.remove(marketSymbol);
        }
    }
}
