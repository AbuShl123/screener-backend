package dev.abu.screener_backend.analysis;

import dev.abu.screener_backend.settings.Settings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import static dev.abu.screener_backend.settings.SettingsType.COINS;
import static dev.abu.screener_backend.settings.SettingsType.DOLLAR;
import static java.lang.Math.*;

@Slf4j
public class TradeList {

    private final LinkedList<Trade> availableObjects;
    @Getter private final PriorityQueue<Trade> bids;
    @Getter private final PriorityQueue<Trade> asks;
    @Getter private final Settings settings;
    @Getter private int highestLevel;

    public TradeList(Settings settings) {
        var comparator = Comparator
                .comparingInt(Trade::getLevel)
                .thenComparingDouble(Trade::getQuantity)
                .thenComparingDouble(Trade::getPrice);
        bids = new PriorityQueue<>(comparator);
        asks = new PriorityQueue<>(comparator);
        availableObjects = new LinkedList<>();
        this.settings = settings;
        init();
    }

    private void init() {
        for (int i = 0; i < 10; i++) {
            availableObjects.add(new Trade());
        }
    }

    public void process(
            Map<Double, Double> bidsMap,
            Map<Double, Double> asksMap,
            Map<Double, Long> bidsTime,
            Map<Double, Long> asksTime,
            double marketPrice
    ) {
        highestLevel = 0;
        availableObjects.addAll(bids);
        availableObjects.addAll(asks);
        bids.clear();
        asks.clear();

        keep5LargestTrades(marketPrice, bidsMap, bidsTime, bids);
        keep5LargestTrades(marketPrice, asksMap, asksTime, asks);
    }

    private void keep5LargestTrades(
            double marketPrice,
            Map<Double, Double> orders,
            Map<Double, Long> timeDetected,
            PriorityQueue<Trade> pq
    ) {
        for (var priceLevel : orders.entrySet()) {

            double price = priceLevel.getKey();
            double quantity = priceLevel.getValue();
            double dist = getDistance(price, marketPrice);
            int level = getLevel(price, quantity, dist);
            long time = timeDetected.getOrDefault(price, System.currentTimeMillis());

            // if pq has 5 elements and the smallest element is greater than the current trade,
            // then there is no need to add it.
            if (pq.size() == 5 && pq.peek().isGreaterThan(level, quantity, price)) {
                continue;
            }

            highestLevel = max(highestLevel, level);

            // otherwise, if pq has 5 elements then remove the smallest element from the pq
            if (pq.size() == 5) {
                availableObjects.addLast(pq.poll());
            }

            Trade trade = availableObjects.pollLast();
            if (trade == null) {
                trade = new Trade(); // fallback
            }
            trade.set(price, quantity, dist, level, time);
            pq.offer(trade);
        }
    }

    private int getLevel(double price, double quantity, double distance) {
        double volume = settings.getSettingsType() == DOLLAR ? price * quantity : quantity;
        LinkedHashMap<Double, Integer> settingsMap = settings.getEntries();

        int level = 0;
        int i = 1;
        for (Map.Entry<Double, Integer> entry : settingsMap.entrySet()) {
            if (distance <= entry.getKey() && volume >= entry.getValue()) {
               level = i;
            }
            i++;
        }

        return level;
    }

    private double getDistance(double price, double marketPrice) {
        double distance = abs((price - marketPrice) / marketPrice * 100);
        return round(distance * 10.0) / 10.0;
    }
}