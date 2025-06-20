package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.OrderBook;
import dev.abu.screener_backend.analysis.TradeList;
import dev.abu.screener_backend.settings.Settings;
import dev.abu.screener_backend.settings.SettingsRepository;
import dev.abu.screener_backend.websockets.SessionManager;
import lombok.Getter;
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

    @Getter private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final ObjectMapper mapper;
    private final SettingsRepository settingsRepository;
    private final SessionManager sessionManager;

    // can be accessed by ~900 order book objects at the same time
    public static void incrementReSyncCount(String websocketName, String symbol) {
        reSyncCountMap.get(websocketName).add(symbol);
    }

    // can be accessed by ~900 order book objects at the same time
    public static void decrementReSyncCount(String websocketName, String symbol) {
        reSyncCountMap.get(websocketName).remove(symbol);
    }

    // can be accessed by ~900 order book objects at the same time
    public static void scheduleTask(Runnable task, boolean isSpot) {
        if (isSpot) spotExecService.submit(task);
        else futExecService.submit(task);
    }

    // accessed by spot & futures websocket only once and sequentially
    public static void prepareReSyncMap(String websocketName) {
        reSyncCountMap.computeIfAbsent(websocketName, k -> ConcurrentHashMap.newKeySet());
    }

    // accessed by binance service every minute
    public static void printReSyncMap() {
        StringBuilder sb = new StringBuilder("re-sync map: {");
        for (var entry : reSyncCountMap.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue().size()).append(", ");
        }
        if (sb.toString().endsWith(", ")) sb.delete(sb.length() - 2, sb.length());
        sb.append("}");
        log.info(sb.toString());
    }

    // can be accessed by 2 message handlers at the same time (2 objects)
    public OrderBook getOrderBook(String marketSymbol) {
        return orderBooks.get(marketSymbol);
    }

    // can be accessed by 2 message handlers at the same time (2 objects)
    public long getNumOfScheduledTasks(boolean isSpot) {
        if (isSpot) return spotExecService.getQueue().size();
        return futExecService.getQueue().size();
    }

    // can be accessed by spot & futures websocket at the same time (2 objects)
    public void prepareOrderBooks(Collection<String> symbols, boolean isSpot, String wsName) {
        Settings defaultSettings = getDefaultSettings();
        for (String symbol : symbols) {
            String mSymbol = isSpot ? symbol : symbol + FUT_SIGN;
            Settings settings = findDefaultSettings(mSymbol).orElse(defaultSettings);
            TradeList tl = new TradeList(settings);
            var orderbook = new OrderBook(mSymbol, isSpot, wsName, mapper, sessionManager, tl);
            orderBooks.putIfAbsent(mSymbol, orderbook);
        }
    }

    private Settings getDefaultSettings() {
        Optional<Settings> defaultSettingsOpt = settingsRepository.findDefaultSettings();
        if (defaultSettingsOpt.isEmpty()) {
            log.warn("Couldn't find default settings!!!");
            throw new RuntimeException("Couldn't find default settings!!!");
        }
        return defaultSettingsOpt.get();
    }

    private Optional<Settings> findDefaultSettings(String mSymbol) {
        return settingsRepository.findDefaultSettings(mSymbol);
    }

    // can be accessed by spot & futures websocket at the same time (2 objects)
    public void dropOrderBooks(Collection<String> symbols, boolean isSpot) {
        for (String symbol : symbols) {
            String marketSymbol = isSpot ? symbol : symbol + FUT_SIGN;
            orderBooks.remove(marketSymbol);
        }
    }

    public void addNewTL(Settings settings) {
        getOrderBook(settings.getMSymbol()).addNewTL(settings);
    }

    public void removeTL(Settings settings) {
        getOrderBook(settings.getMSymbol()).removeTL(settings);
    }

    public void truncateOrderBooks() {
        long start = System.nanoTime();
        for (OrderBook orderBook : orderBooks.values()) {
            orderBook.getGeneralTradeList().truncateOrderBook();
        }
        long duration = (System.nanoTime() - start) / 1_000_000;
        log.info("Truncated all the order books in {}ms", duration);
    }
}
