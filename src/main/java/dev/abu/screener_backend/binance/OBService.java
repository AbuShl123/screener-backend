package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.OrderBook;
import dev.abu.screener_backend.analysis.TradeList;
import dev.abu.screener_backend.settings.Settings;
import dev.abu.screener_backend.settings.SettingsRepository;
import dev.abu.screener_backend.websockets.EventDistributor;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Service
@Slf4j
@RequiredArgsConstructor
public class OBService {
    private static final Map<String, Set<String>> reSyncCountMap = new ConcurrentHashMap<>();

    @Getter
    private final Map<String, OrderBook> orderBooks = new ConcurrentHashMap<>();
    private final Map<OrderBook, Set<Settings>> settingsMap = new ConcurrentHashMap<>();
    private final Map<Settings, TradeList> tradeLists = new ConcurrentHashMap<>();
    private final Map<Settings, Integer> settingsCount = new ConcurrentHashMap<>();

    private final ObjectMapper mapper;
    private final EventDistributor eventDistributor;
    private final SettingsRepository settingsRepository;
    private Settings allSymbolDefSettings;

    @PostConstruct
    public void init() {
        allSymbolDefSettings = settingsRepository.findDefaultSettingsForAllSymbols()
                .orElseThrow(() -> new RuntimeException("No default settings for all symbols found!!"));
    }

    public static void incrementReSyncCount(String websocketName, String symbol) {
        reSyncCountMap.get(websocketName).add(symbol);
    }

    public static void decrementReSyncCount(String websocketName, String symbol) {
        reSyncCountMap.get(websocketName).remove(symbol);
    }

    public static void prepareReSyncMap(String websocketName) {
        reSyncCountMap.computeIfAbsent(websocketName, k -> ConcurrentHashMap.newKeySet());
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

    public OrderBook getOrderBook(String marketSymbol) {
        return orderBooks.get(marketSymbol);
    }

    public void prepareOrderBooks(Collection<String> symbols, boolean isSpot, String wsName) {
        for (String symbol : symbols) {
            String mSymbol = isSpot ? symbol : symbol + FUT_SIGN;
            var orderbook = new OrderBook(mSymbol, isSpot, wsName, mapper, eventDistributor);
            orderBooks.putIfAbsent(mSymbol, orderbook);
            addDefaultTL(orderbook);
        }
    }

    public void dropOrderBooks(Collection<String> symbols, boolean isSpot) {
        for (String symbol : symbols) {
            String marketSymbol = isSpot ? symbol : symbol + FUT_SIGN;
            orderBooks.remove(marketSymbol);
        }
    }

    private void addDefaultTL(OrderBook orderBook) {
        settingsMap.computeIfAbsent(orderBook, k -> ConcurrentHashMap.newKeySet());
        settingsRepository.findDefaultSettings(orderBook.getMSymbol())
                .ifPresentOrElse(
                        (s) -> addTL(orderBook, s),
                        () -> addTL(orderBook, allSymbolDefSettings)
                );
    }

    public void addUserTL(Collection<Settings> userSettings) {
        for (Settings settings : userSettings) {
            if (settings.getSettingsHash().contains("default")) continue;

            String mSymbol = settings.getMSymbol();
            OrderBook ob = orderBooks.get(mSymbol);

            if (ob == null) {
                log.warn("Order book not found for mSymbol: {}", mSymbol);
                continue;
            }

            if (!settingsMap.containsKey(ob)) {
                log.warn("{} order book has no DTL", mSymbol);
                continue;
            }

            if (settingsMap.get(ob).contains(settings)) {
                settingsCount.put(settings, settingsCount.get(settings) + 1);
                continue;
            }

            addTL(ob, settings);
        }
    }

    public void deleteUserTL(Collection<Settings> userSettings) {
        for (Settings settings : userSettings) {
            if (settings.getSettingsHash().contains("default")) continue;
            if (!settingsCount.containsKey(settings)) continue;
            settingsCount.put(settings, settingsCount.get(settings) - 1);
            if (settingsCount.get(settings) == 0) {
                TradeList tl = tradeLists.remove(settings);
                OrderBook ob =  orderBooks.get(settings.getMSymbol());

                settingsMap.get(ob).remove(settings);
                settingsMap.remove(ob);
                ob.removeTL(tl);
            }
        }
    }

    private void addTL(OrderBook ob, Settings settings) {
        TradeList tl = new TradeList(settings, ob.getMSymbol());
        settingsMap.get(ob).add(settings);
        tradeLists.put(settings, tl);
        settingsCount.put(settings, 1);
        ob.addTL(tl);
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
