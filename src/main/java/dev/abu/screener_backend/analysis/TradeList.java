package dev.abu.screener_backend.analysis;

import dev.abu.screener_backend.settings.Settings;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static dev.abu.screener_backend.settings.SettingsType.DOLLAR;
import static java.lang.Math.*;

@Slf4j
public class TradeList {

    private static final int MAX_TRADES = 5;

    @Getter private final String mSymbol;
    @Getter private final Settings settings;
    @Getter private int maxLevel;
    @Getter private int prevMaxLevel;

    private final LinkedList<Trade> availableObjects;
    private final PriorityQueue<Trade> bids;
    private final PriorityQueue<Trade> asks;


    public TradeList(Settings settings, String mSymbol) {
        var comparator = Comparator
                .comparingInt(Trade::getLevel)
                .thenComparingDouble(Trade::getQuantity)
                .thenComparingDouble(Trade::getPrice);
        this.bids = new PriorityQueue<>(comparator);
        this.asks = new PriorityQueue<>(comparator);
        this.availableObjects = new LinkedList<>();
        this.settings = settings;
        this.mSymbol = mSymbol;
        init();
    }

    private void init() {
        for (int i = 0; i < 10; i++) {
            availableObjects.add(new Trade());
        }
    }

    public void process(GeneralTradeList gtl) {
        analyzeNewData(gtl);
    }

    public TradeListDTO toDTO() {
        return new TradeListDTO(mSymbol, bids, asks);
    }

    private void analyzeNewData(GeneralTradeList gtl) {
        prevMaxLevel = maxLevel;
        maxLevel = 0;
        availableObjects.addAll(bids);
        availableObjects.addAll(asks);
        bids.clear();
        asks.clear();
        keepLargestTrades(gtl.getMarketPrice(), gtl.getBids(), gtl.getBidsTimeMap(), bids);
        keepLargestTrades(gtl.getMarketPrice(), gtl.getAsks(), gtl.getAsksTimeMap(), asks);
    }

    private void keepLargestTrades(
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
            if (pq.size() == MAX_TRADES && pq.peek().isGreaterThan(level, quantity, price)) {
                continue;
            }

            maxLevel = max(maxLevel, level);

            // otherwise, if pq has 5 elements then remove the smallest element from the pq
            if (pq.size() == MAX_TRADES) {
                availableObjects.addLast(pq.poll());
            }

            Trade trade = availableObjects.pollLast();
            if (trade == null) {
                trade = new Trade(); // fallback, should never happen actually
                log.warn("Trade pool exhausted, creating new Trade instance.");
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