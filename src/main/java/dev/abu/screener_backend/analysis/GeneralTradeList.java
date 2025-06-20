package dev.abu.screener_backend.analysis;

import dev.abu.screener_backend.binance.depth.PriceLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static java.lang.Math.abs;
import static java.lang.Math.round;

@Getter
@Slf4j
public class GeneralTradeList {

    private static final double MAX_PERCENT_DISTANCE_FROM_MARKET = 10;
    private static final int TRUNCATE_THRESHOLD = 10_000;
    private static final int OPTIMAL_THRESHOLD = 10_000;

    // bids sorted desc
    private final TreeMap<Double, Double> bids = new TreeMap<>(Comparator.reverseOrder());
    // asks sorted asc
    private final TreeMap<Double, Double> asks = new TreeMap<>();
    private final Map<Double, Long> bidsTimeMap = new HashMap<>();
    private final Map<Double, Long> asksTimeMap = new HashMap<>();

    public synchronized void clear() {
        bids.clear();
        asks.clear();
    }

    public synchronized void truncateOrderBook() {
        truncateOrders(bids);
        truncateOrders(asks);
    }

    public synchronized void process(List<PriceLevel> bids, List<PriceLevel> asks, boolean initialSnapshot) {
        if (initialSnapshot) processInitialSnapshot(bids, asks);
        else processUpdate(bids, asks);
    }

    public synchronized double getMarketPrice() {
        if (bids.isEmpty() || asks.isEmpty()) {
            throw new IllegalStateException("Order book is not initialized.");
        }
        return (bids.firstKey() + asks.firstKey()) / 2;
    }

    private void truncateOrders(TreeMap<Double, Double> orders) {
        if (orders.size() < TRUNCATE_THRESHOLD) return;

        PriorityQueue<Map.Entry<Double, Double>> container = new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue));
        List<Map.Entry<Double, Double>> topEntries = new ArrayList<>();

        while (orders.size() > OPTIMAL_THRESHOLD) {
            int furthestDistance = (int) round(getOrderDistance(orders.lastKey()));
            get5LargestEntriesByValue(furthestDistance - 1, orders, container);
            topEntries.addAll(container);
            container.clear();
        }

        topEntries.forEach(e -> orders.put(e.getKey(), e.getValue()));
    }

    private void get5LargestEntriesByValue(int until, TreeMap<Double, Double> orders, PriorityQueue<Map.Entry<Double, Double>> pq) {
        var it = orders.descendingMap().entrySet().iterator();
        while (it.hasNext()) {
            if (orders.size() <= OPTIMAL_THRESHOLD) break;
            Map.Entry<Double, Double> entry = it.next();
            int dist = (int) round(getOrderDistance(entry.getKey()));
            if (dist < until) break;
            if (pq.size() < 5) {
                pq.offer(entry);
            } else if (entry.getValue() > pq.peek().getValue()) {
                pq.poll();
                pq.offer(entry);
            }
            it.remove();
        }
    }

    private void processInitialSnapshot(List<PriceLevel> bids, List<PriceLevel> asks) {
        double marketPrice = (bids.get(0).price() + asks.get(0).price()) / 2;
        processSnapshotLevels(asks, marketPrice, true);
        processSnapshotLevels(bids, marketPrice, false);
    }

    private void processUpdate(List<PriceLevel> bids, List<PriceLevel> asks) {
        processUpdateLevels(bids, false);
        processUpdateLevels(asks, true);
    }

    private void processSnapshotLevels(List<PriceLevel> priceLevels, double marketPrice, boolean isAsk) {
        var map = isAsk ? asks : bids;
        Map<Double, Long> timeMap = isAsk ? asksTimeMap : bidsTimeMap;

        for (PriceLevel priceLevel : priceLevels) {
            double price = priceLevel.price();
            double qty = priceLevel.quantity();

            double distance = abs((price - marketPrice) / marketPrice * 100);
            if (distance > MAX_PERCENT_DISTANCE_FROM_MARKET) break;

            map.put(price, qty);
            recordPriceLevel(price, timeMap);
        }
    }

    private void processUpdateLevels(List<PriceLevel> priceLevels, boolean isAsk) {
        TreeMap<Double, Double> map = isAsk ? asks : bids;
        Map<Double, Long> timeMap = isAsk ? asksTimeMap : bidsTimeMap;

        for (PriceLevel priceLevel : priceLevels) {
            double price = priceLevel.price();
            double qty = priceLevel.quantity();

            if (qty == 0) {
                map.remove(price);
                timeMap.remove(price);
                continue;
            }

            if (map.containsKey(price)) {
                map.put(price, qty);
                continue;
            }

            double distance = getOrderDistance(price);
            if (distance > MAX_PERCENT_DISTANCE_FROM_MARKET) continue;
            map.put(price, qty);
            recordPriceLevel(price, timeMap);
        }
    }

    private double getOrderDistance(double orderPrice) {
        double marketPrice = getMarketPrice();
        return abs((orderPrice - marketPrice) / marketPrice * 100);
    }

    private void recordPriceLevel(double price, Map<Double, Long> timeMap) {
        timeMap.putIfAbsent(price, System.currentTimeMillis());
    }
}
