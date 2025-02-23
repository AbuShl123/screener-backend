package dev.abu.screener_backend.analysis;

import dev.abu.screener_backend.entity.Trade;
import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static dev.abu.screener_backend.utils.EnvParams.CUP_SIZE;
import static dev.abu.screener_backend.utils.EnvParams.MAX_INCLINE;

@Getter
public class TradeList {
    private final Map<Integer, TreeSet<Trade>> bids = new HashMap<>();
    private final Map<Integer, TreeSet<Trade>> asks = new HashMap<>();
    private final String symbol;

    private long lastUpdateTime = System.currentTimeMillis();

    TradeList(String symbol) {
        this.symbol = symbol;
        int n = MAX_INCLINE;
        while (n >= -MAX_INCLINE) {
            bids.put(n, new TreeSet<>());
            asks.put(n, new TreeSet<>());
            n -= 5;
        }
    }

    public void clear() {
        bids.forEach((key, value) -> value.clear());
        asks.forEach((key, value) -> value.clear());
    }

    void addTrade(String price, double qty, double incline, boolean isAsk, long timestamp) {
        int level = getLevel(incline);
        if (isAsk) {
            addTrade(asks.get(level), price, qty, incline, timestamp);
        } else {
            addTrade(bids.get(level), price, qty, incline, timestamp);
        }
        updateDensities();
    }

    private boolean addTrade(TreeSet<Trade> orderBook, String price, double qty, double incline, long timestamp) {
        // case when price level should be removed
        if (qty == 0) {
            return orderBook.removeIf(trade -> trade.getPrice().equals(price));
        }

        // case when there is already a trade with a given price
        for (Trade trade : orderBook) {
            if (trade.getPrice().equals(price)) {
                trade.setQuantity(qty);
                return true;
            }
        }

        int density = DensityAnalyzer.getDensityAnalyzer(symbol).getDensity(qty);

        // case when there are not enough trades in the order book
        if (orderBook.isEmpty() || orderBook.size() < CUP_SIZE) {
            orderBook.add(new Trade(price, qty, incline, density, timestamp));
            return true;
        }

        // if new trade is smaller than all the current trades in the list, then no need to add it
        if (orderBook.first().getQuantity() > qty) return false;

        // otherwise, we will add this trade
        orderBook.add(new Trade(price, qty, incline, density, timestamp));

        // making sure that size won't exceed the given cup size
        if (orderBook.size() > CUP_SIZE) {
            orderBook.pollFirst();
        }

        return true;
    }

    private void updateDensities() {
        if (System.currentTimeMillis() - lastUpdateTime < 10_000) return;
        lastUpdateTime = System.currentTimeMillis();
        final var analyzer = DensityAnalyzer.getDensityAnalyzer(symbol);
        bids.values().stream().flatMap(Set::stream).forEach(t -> t.setDensity(analyzer.getDensity(t.getQuantity())));
        asks.values().stream().flatMap(Set::stream).forEach(t -> t.setDensity(analyzer.getDensity(t.getQuantity())));
    }

    private int getLevel(double incline) {
        int n = (int) incline;
        int ost = n % 5;
        if (n == 0 || ost == 0) return n;
        int level;
        if (n > 0) {
            level = n + (5 - ost);
        } else {
            level = n - (5 + ost);
        }
        return level;
    }

    public int getMaxDensity() {
        int maxBidDensity = getMaxDensity(false);
        int maxAskDensity = getMaxDensity(true);
        return Math.max(maxBidDensity, maxAskDensity);
    }

    private int getMaxDensity(boolean isAsk) {
        var trades = isAsk ? asks : bids;
        int maxDensity = 0;

        for (TreeSet<Trade> tradeList : trades.values()) {
            if (tradeList.isEmpty()) continue;
            Trade t = tradeList.last();
            if (maxDensity < t.getDensity()) maxDensity = t.getDensity();
        }

        return maxDensity;
    }

    public Trade getMaxTrade(boolean isAsk) {
        var trades = isAsk ? asks : bids;

        Trade max = null;
        for (TreeSet<Trade> tradeList : trades.values()) {
            if (tradeList.isEmpty()) continue;
            Trade t = tradeList.last();
            if (max == null || max.getQuantity() < t.getQuantity()) max = t;
        }

        return max;
    }
}
