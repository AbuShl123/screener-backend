package dev.abu.screener_backend.analysis;

import dev.abu.screener_backend.websockets.SessionPool;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static dev.abu.screener_backend.utils.EnvParams.CUP_SIZE;
import static dev.abu.screener_backend.utils.EnvParams.MAX_INCLINE;
import static java.lang.Math.abs;
import static java.lang.Math.round;

@Slf4j
public class TradeList {
    private final Map<Double, Long> backup = new HashMap<>();
    private final TreeSet<Trade> bids = new TreeSet<>();
    private final TreeSet<Trade> asks = new TreeSet<>();
    private final Map<Double, Trade> bidsMap = new HashMap<>();
    private final Map<Double, Trade> asksMap = new HashMap<>();
    private final LevelAnalyzer levelAnalyzer;
    @Getter
    private final String mSymbol;
    private final SessionPool sessionPool;

    TradeList(String mSymbol, SessionPool sessionPool) {
        this.mSymbol = mSymbol;
        this.sessionPool = sessionPool;
        this.levelAnalyzer = new LevelAnalyzer();
    }

    public void clear() {
        sessionPool.removeData(mSymbol);
        backup.clear();
        bids.forEach(t -> backup.put(t.getPrice(), t.getLife()));
        asks.forEach(t -> backup.put(t.getPrice(), t.getLife()));
        bids.clear();
        asks.clear();
        bidsMap.clear();
        asksMap.clear();
    }

    public synchronized void addTrade(double price, double qty, double distance, boolean isAsk, long timestamp) {
        processTrade(price, qty, distance, isAsk, timestamp);
//        checkDataInvariants();
        if (getMaxLevel() >= 1) {
            sessionPool.addData(new TradeListDTO(mSymbol, bids, asks));
        } else {
            sessionPool.removeData(mSymbol);
        }
    }

    public synchronized void updateLevelsAndDistances(double marketPrice) {
        updateLevelsAndDistances(true, marketPrice);
        updateLevelsAndDistances(false, marketPrice);
    }

    public Trade getMaxTrade(boolean isAsk) {
        TreeSet<Trade> trades = getTradeSet(isAsk);
        if (trades.isEmpty()) return null;
        return trades.last();
    }

    public int getMaxLevel() {
        return Math.max(
                asks.isEmpty() ? -1 : asks.last().getLevel(),
                bids.isEmpty() ? -1 : bids.last().getLevel()
        );
    }

    private void processTrade(double price, double qty, double distance, boolean isAsk, long timestamp) {
        var tradeSet = getTradeSet(isAsk);
        var tradeMap = getTradeMap(isAsk);

        // case when price level should be removed
        if (qty == 0) {
            removeTradeByPrice(isAsk, price);
            return;
        }

        // case when there is already a trade with the given price
        if (tradeMap.containsKey(price)) {
            updateQuantity(isAsk, price, qty);
            return;
        }

        // add trade IF:
        // 1) size of tree set is less than cup_size
        // 2) OR IF smallest trade in the tree set is smaller than the new trade
        int level = levelAnalyzer.getLevel(price, qty, distance, mSymbol);
        if (tradeSet.size() < CUP_SIZE || tradeSet.first().compareToRawValues(level, qty, price) < 0) {
            timestamp = loadTimestampFromMemory(price, timestamp);
            addNewTrade(isAsk, price, qty, distance, level, timestamp);

            // making sure that size won't exceed the given cup size
            if (tradeSet.size() > CUP_SIZE) {
                removeSmallestTrade(isAsk);
            }
        }
    }

    private void addNewTrade(boolean isAsk, double price, double qty, double distance, int level, long timestamp) {
        Trade trade = new Trade(price, qty, distance, level, timestamp);
        if (getTradeSet(isAsk).add(trade)) {
            getTradeMap(isAsk).put(price, trade);
        }
    }

    private void updateQuantity(boolean isAsk, double price, double quantity) {
        var tradeSet = getTradeSet(isAsk);
        var t = getTradeMap(isAsk).get(price);

        tradeSet.remove(t);
        t.setQuantity(quantity);
        updateLevel(t);
        tradeSet.add(t);
    }

    private void removeTradeByPrice(boolean isAsk, double price) {
        Trade trade = getTradeMap(isAsk).remove(price);
        if (trade != null) {
            if (!getTradeSet(isAsk).remove(trade)) log.error("Couldn't remove trade from set: {}", trade);
        }
    }

    private void removeSmallestTrade(boolean isAsk) {
        Trade trade = getTradeSet(isAsk).pollFirst();
        if (trade != null) {
            if (getTradeMap(isAsk).remove(trade.getPrice()) == null) {
                log.error("Couldn't remove trade from map: {}", trade);
            }
        }
    }

    private void updateLevelsAndDistances(boolean isAsk, double marketPrice) {
        var set = getTradeSet(isAsk);
        set.clear();

        var it = getTradeMap(isAsk).values().iterator();
        while (it.hasNext()) {
            var t = it.next();
            double newDistance = getDistance(t.getPrice(), marketPrice);
            if (newDistance >= MAX_INCLINE) {
                it.remove();
                continue;
            }
            t.setDistance(newDistance);
            updateLevel(t);
            set.add(t);
        }
    }

    private double getDistance(double price, double marketPrice) {
        double distance = abs((price - marketPrice) / marketPrice * 100);
        return round(distance * 100.0) / 100.0;
    }

    private void updateLevel(Trade trade) {
        trade.setLevel(levelAnalyzer.getLevel(trade.getPrice(), trade.getQuantity(), trade.getDistance(), mSymbol));
    }

    private long loadTimestampFromMemory(double price, long timestamp) {
        long timestampFromMemory = backup.getOrDefault(price, -1L);
        if (timestampFromMemory > 0) return timestampFromMemory;
        return timestamp;
    }

    private TreeSet<Trade> getTradeSet(boolean isAsk) {
        return isAsk ? asks : bids;
    }

    private Map<Double, Trade> getTradeMap(boolean isAsk) {
        return isAsk ? asksMap : bidsMap;
    }

    private void checkDataInvariants() {
        boolean failedBids = false;
        if (bids.size() != bidsMap.size()) {
            failedBids = true;
        } else {
            for (Trade bid : bids) {
                if (!bidsMap.containsKey(bid.getPrice())) {
                    failedBids = true;
                    break;
                }
            }
        }
        if (failedBids) {
            log.error("bids set and bids map are not equal: bidsSet={}, bidsMap={}", bids, bidsMap);
        }

        boolean failedAsks = false;
        if (asks.size() != asksMap.size()) {
            failedAsks = true;
        } else {
            for (Trade ask : asks) {
                if (!asksMap.containsKey(ask.getPrice())) {
                    failedAsks = true;
                    break;
                }
            }
        }
        if (failedAsks) {
            log.error("asks set and asks map are not equal: asksSet={}, asksMap={}", asks, asksMap);
        }

        if (failedBids || failedAsks) System.exit(1);
    }
}