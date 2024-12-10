package dev.abu.screener_backend;

import lombok.Getter;
import lombok.Setter;

import java.util.*;

public class Main {
    private static final int MAX_INCLINE = 30;
    private static final int CUP_SIZE = 5;
    private final static Random rand = new Random();
    private final static TradeList orderbook = new TradeList();

    @Getter
    @Setter
    public static class Trade implements Comparable<Trade> {
        public double price;
        public double quantity;
        public double incline;

        public Trade(double price, double quantity) {
            this(price, quantity, 0.0);
        }

        public Trade(double price, double quantity, double incline) {
            this.price = price;
            this.quantity = quantity;
            this.incline = incline;
        }

        @Override
        public String toString() {
            return "" + price + '\t' + quantity + "\t\t" + incline + '%';
        }

        @Override
        public int compareTo(Trade another) {
            double qty1 = getQuantity();
            double qty2 = another.getQuantity();
            if (qty1 == qty2) return 1;
            return (int) (qty1 - qty2);
        }
    }

    public static void main(String[] args) {
        mockTradeList();
//        printTradeList();

        List<Trade> bids = getbids(10, 30);
        List<Trade> asks = getAsks(10, 30);
        asks.forEach(System.out::println);
        System.out.println("------------");
        bids.forEach(System.out::println);
    }

    private static int getLevel(double incline) {
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

    public static List<Trade> getbids(int lowBound, int highBound) { // l = 5, h = 30
        return getTrades(orderbook.getBids(), lowBound, highBound);
    }

    public static List<Trade> getAsks(int lowBound, int highBound) {
        return getTrades(orderbook.getAsks(), lowBound, highBound);
    }

    private static List<Trade> getTrades(Map<Integer, TreeSet<Trade>> tradeMap, int lowBound, int highBound) {
        int lowLever = getLevel(lowBound);
        int highLever = getLevel(highBound);

        List<Trade> tradeList =
                tradeMap.entrySet().stream()
                        .filter(entry -> entry.getKey() >= lowLever && entry.getKey() <= highLever)
                        .flatMap(entry -> entry.getValue().stream())
                        .sorted().toList();

        return tradeList.subList(Math.max(tradeList.size() - CUP_SIZE, 0), tradeList.size());
    }

    @Getter
    private static class TradeList {
        private final Map<Integer, TreeSet<Trade>> bids = new HashMap<>();
        private final Map<Integer, TreeSet<Trade>> asks = new HashMap<>();

        TradeList() {
            int n = MAX_INCLINE;
            while (n >= -MAX_INCLINE) {
                bids.put(n, new TreeSet<>());
                asks.put(n, new TreeSet<>());
                n -= 5;
            }
        }

        public void addTrade(double price, double qty, double incline, boolean isAsk) {
            int level = getLevel(incline);
            if (isAsk) addTrade(asks.get(level), price, qty, incline);
            else addTrade(bids.get(level), price, qty, incline);
        }

        private void addTrade(TreeSet<Trade> orderbook,
                              double price,
                              double qty,
                              double incline
        ) {
            // case when there are 0 trades in the order book
            if (orderbook.isEmpty() || orderbook.size() < CUP_SIZE) {
                orderbook.add(new Trade(price, qty, incline));
                return;
            }

            // case when price level should be removed
            if (qty == 0) {
                orderbook.removeIf(trade -> trade.getPrice() == price);
                return;
            }

            // case when there is already a trade with given price
            if (orderbook.removeIf(trade -> trade.getPrice() == price)) {
                orderbook.add(new Trade(price, qty));
                return;
            }

            // if new trade is lower than all current trades in ob, then no need to add it
            if (orderbook.first().getQuantity() > qty) return;

            orderbook.add(new Trade(price, qty));
            // making sure that size won't exceed given cup size
            if (orderbook.size() > CUP_SIZE) {
                orderbook.pollFirst();
            }
        }
    }

    private static void mockTradeList() {
        int marketPrice = 100; // 70 - 130
        for (int i = 70; i <= 130; i++) {
            int incline = (int) (((double) i / marketPrice - 1) * 100);
            orderbook.addTrade(i, rand.nextInt(10)+1, incline, false);
            orderbook.addTrade(i, rand.nextInt(10)+1, incline, true);
        }
    }

    private static void printTradeList() {
        var bids = orderbook.bids;
        var asks = orderbook.asks;

        for (Map.Entry<Integer, TreeSet<Trade>> entry : bids.entrySet()) {
            System.out.println(entry.getKey() + " : ");
            var askSet = asks.get(entry.getKey());
            var bidSet = entry.getValue();

            askSet.forEach(trade -> System.out.printf("\t%f\t%f\t%.2f%s\n", trade.price, trade.quantity, trade.incline, "%"));
            System.out.println("\t----------------");
            bidSet.forEach(trade -> System.out.printf("\t%f\t%f\t%.2f%s\n", trade.price, trade.quantity, trade.incline, "%"));
        }
    }
}
