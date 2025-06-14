package dev.abu.screener_backend.analysis;

import dev.abu.screener_backend.binance.depth.PriceLevel;

import java.util.*;

import static dev.abu.screener_backend.utils.EnvParams.MAX_INCLINE;
import static java.lang.Double.parseDouble;
import static java.lang.Math.abs;

public class GeneralTradeList {

    // bids sorted desc
    private final TreeMap<Double, Double> bids = new TreeMap<>(Comparator.reverseOrder());
    // asks sorted asc
    private final TreeMap<Double, Double> asks = new TreeMap<>();

    private final Map<Double, Long> bidsTimeMap = new HashMap<>();
    private final Map<Double, Long> asksTimeMap = new HashMap<>();

    public void processInitialSnapshot(List<PriceLevel> bids, List<PriceLevel> asks) {
        double marketPrice = (parseDouble(bids.get(0).price()) - parseDouble(asks.get(0).price())) / 2;
        traverseSnapshotUpdate(bids, marketPrice, true);
        traverseSnapshotUpdate(bids, marketPrice, false);
    }

    private void traverseSnapshotUpdate(List<PriceLevel> orders, double marketPrice, boolean isAsk) {
        var map = isAsk ? asks : bids;
        for (PriceLevel priceLevel : orders) {
            double price = parseDouble(priceLevel.price());
            double qty = parseDouble(priceLevel.quantity());

            double distance = abs((price - marketPrice) / marketPrice * 100);
            if (distance > MAX_INCLINE) break;

            map.put(price, qty);
        }
    }

    public void process(List<PriceLevel> bids, List<PriceLevel> asks) {
        traverseDepthUpdate(bids, false);
        traverseDepthUpdate(asks, true);
    }

    private void traverseDepthUpdate(List<PriceLevel> orders, boolean isAsk) {
        TreeMap<Double, Double> map = isAsk ? asks : bids;
        Map<Double, Long> timeMap = isAsk ? asksTimeMap : bidsTimeMap;

        for (PriceLevel order : orders) {
            double price = parseDouble(order.price());
            double qty = parseDouble(order.quantity());

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
            if (distance > MAX_INCLINE) continue;
            map.put(price, qty);
            timeMap.put(price, System.currentTimeMillis());
        }
    }

    public double getOrderDistance(double orderPrice) {
        double marketPrice = getMarketPrice();
        return abs((orderPrice - marketPrice) / marketPrice * 100);
    }

    public double getMarketPrice() {
        return (bids.firstKey() + asks.firstKey()) / 2;
    }
}
