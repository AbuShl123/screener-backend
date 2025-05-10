package dev.abu.screener_backend.analysis;

import dev.abu.screener_backend.binance.Trade;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;

import static dev.abu.screener_backend.analysis.DensityAnalyzer.getLevel;
import static dev.abu.screener_backend.utils.EnvParams.CUP_SIZE;

@Getter
public class TradeList {
    private static final double EPSILON = 1e-8;

    private final Map<Double, Long> backup = new HashMap<>();
    private final TreeSet<Trade> bids = new TreeSet<>();
    private final TreeSet<Trade> asks = new TreeSet<>();
    private final String symbol;

    private long lastUpdateTime = System.currentTimeMillis();

    TradeList(String symbol) {
        this.symbol = symbol;
    }

    public void clear() {
        backup.clear();
        bids.forEach(t -> backup.put(t.getPrice(), t.getLife()));
        asks.forEach(t -> backup.put(t.getPrice(), t.getLife()));
        bids.clear();
        asks.clear();
    }

    void addTrade(double price, double qty, double distance, boolean isAsk, long timestamp) {
        long timestampFromMemory = backup.getOrDefault(price, -1L);
        if (timestampFromMemory > 0) timestamp = timestampFromMemory;
        if (isAsk) {
            addTrade(asks, price, qty, distance, timestamp);
        } else {
            addTrade(bids, price, qty, distance, timestamp);
        }
        updateLevels();
    }

    private boolean addTrade(TreeSet<Trade> orderBook, double price, double qty, double distance, long timestamp) {
        // case when price level should be removed
        if (qty == 0) {
            return orderBook.removeIf(trade -> trade.getPrice() == price);
        }

        // case when there is already a trade with the given price
        for (Trade trade : orderBook) {
            if (Math.abs(trade.getPrice() - price) < EPSILON) {
                trade.setQuantity(qty);
                return true;
            }
        }

        int level = getLevel(price, qty, distance, symbol);

        // case when there are not enough trades in the order book
        if (orderBook.isEmpty() || orderBook.size() < CUP_SIZE) {
            orderBook.add(new Trade(price, qty, distance, level, timestamp));
            return true;
        }

        // if new trade is smaller than all the current trades in the list, then no need to add it
        if (orderBook.first().getQuantity() > qty) return false;

        // otherwise, we will add this trade
        orderBook.add(new Trade(price, qty, distance, level, timestamp));

        // making sure that size won't exceed the given cup size
        if (orderBook.size() > CUP_SIZE) {
            orderBook.pollFirst();
        }

        return true;
    }

    private void updateLevels() {
        if (System.currentTimeMillis() - lastUpdateTime < 20_000) return;
        lastUpdateTime = System.currentTimeMillis();

        bids.forEach(t -> t.setLevel(
                getLevel(t.getPrice(), t.getQuantity(), t.getDistance(), symbol)
        ));

        asks.forEach(t -> t.setLevel(
                getLevel(t.getPrice(), t.getQuantity(), t.getDistance(), symbol)
        ));
    }

    public Trade getMaxTrade(boolean isAsk) {
        TreeSet<Trade> trades = isAsk ? asks : bids;
        if (trades.isEmpty()) return null;
        var maxTrade = trades.first();
        for (Trade trade : trades) {
            if (trade.getLevel() > maxTrade.getLevel()) maxTrade = trade;
        }
        return maxTrade;
    }
}