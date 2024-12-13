package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.Tickers;
import dev.abu.screener_backend.entity.Trade;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.*;

import static java.lang.Math.abs;

@Slf4j
public class OrderBookStream {

    public static final int MAX_INCLINE = 30;
    public static final int CUP_SIZE = 5;
    private static final Map<String, OrderBookStream> streams = new HashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    private final String symbol;
    private final TradeList orderBook = new TradeList();
    private final HashSet<Double> quantitiesDataSet = new HashSet<>();
    private final DensityAnalyzer densityAnalyzer;

    @Setter
    private RabbitTemplate rabbitTemplate;

    private OrderBookStream(String symbol) {
        this.symbol = symbol;
        this.densityAnalyzer = DensityAnalyzer.getDensityAnalyzer(symbol);
    }

    public static synchronized OrderBookStream getInstance(String symbol) {
        symbol = symbol.toLowerCase();
        if (!streams.containsKey(symbol)) {
            streams.put(symbol, new OrderBookStream(symbol));
        }
        return streams.get(symbol);
    }

    @Getter
    private class TradeList {
        private final Map<Integer, TreeSet<Trade>> bids = new HashMap<>();
        private final Map<Integer, TreeSet<Trade>> asks = new HashMap<>();

        private TradeList() {
            int n = MAX_INCLINE;
            while (n >= -MAX_INCLINE) {
                bids.put(n, new TreeSet<>());
                asks.put(n, new TreeSet<>());
                n -= 5;
            }
        }

        private boolean addTrade(double price, double qty, double incline, boolean isAsk) {
            int level = getLevel(incline);
            if (isAsk) {
                return addTrade(asks.get(level), price, qty, incline);
            } else {
                return addTrade(bids.get(level), price, qty, incline);
            }
        }

        private boolean addTrade(TreeSet<Trade> orderBook, double price, double qty, double incline) {
            // case when there are 0 trades in the order book
            if (orderBook.isEmpty() || orderBook.size() < CUP_SIZE) {
                orderBook.add(new Trade(price, qty, incline));
                return true;
            }

            // case when price level should be removed
            if (qty == 0) {
                return orderBook.removeIf(trade -> trade.getPrice() == price);
            }

            // case when there is already a trade with a given price
            if (orderBook.removeIf(trade -> trade.getPrice() == price)) {
                orderBook.add(new Trade(price, qty, incline));
                return true;
            }

            // if new trade is lower than all current trades in ob, then no need to add it
            if (orderBook.first().getQuantity() > qty) return false;

            // otherwise, we will add this trade
            orderBook.add(new Trade(price, qty));

            // making sure that size won't exceed the given cup size
            if (orderBook.size() > CUP_SIZE) {
                orderBook.pollFirst();
            }

            return true;
        }
    }

    public synchronized void buffer(String rawData) {
        try {
            boolean hasUpdates;
            JsonNode json = mapper.readTree(rawData);

            var asksArray = getTradeArray(rawData, json, true);
            hasUpdates = traverseArray(asksArray, true);

            var bidsArray = getTradeArray(rawData, json, false);
            hasUpdates = hasUpdates || traverseArray(bidsArray, false);

            if (hasUpdates) {
                boolean haveDensitiesUpdated = densityAnalyzer.analyzeDensities(getQuantitiesDataSet());
                if (haveDensitiesUpdated) quantitiesDataSet.clear();
                sendData();
            }
        } catch (JsonProcessingException e) {
            log.error(e.getMessage());
        }
    }

    private JsonNode getTradeArray(String rawData, JsonNode json, boolean ask) {
        String arrayFull = ask ? "a" : "b";
        String arrayShort = ask ? "asks" : "bids";

        JsonNode data = json.get("data");
        JsonNode array;

        if (data != null) {
            array = data.get(arrayFull);
        } else {
            array = json.get(arrayShort);
        }

        if (array == null) {
            log.error("{} array is null: {}", arrayShort, rawData);
            return null;
        }

        return array;
    }

    private boolean traverseArray(JsonNode array, boolean isAsk) {
        if (array == null || (array.isArray() && array.isEmpty())) {
            return false;
        }

        boolean hasUpdates = false;
        for (JsonNode node : array) {
            var price = node.get(0).asDouble();
            var qty = node.get(1).asDouble();
            hasUpdates = filterByRange(price, qty, isAsk);
        }
        return hasUpdates;
    }

    private boolean filterByRange(double price, double qty, boolean isAsk) {
        double incline = getIncline(price);
        boolean hasUpdates = false;
        if (abs(incline) <= MAX_INCLINE) {
            hasUpdates = orderBook.addTrade(price, qty, incline, isAsk);
            quantitiesDataSet.add(qty);
        }
        return hasUpdates;
    }

    private void sendData() {
        Map<String, Map<Integer, TreeSet<Trade>>> data = new HashMap<>();
        data.put("bids", getBids());
        data.put("asks", getAsks());
        rabbitTemplate.convertAndSend(symbol, data);
    }

    public synchronized Map<Integer, TreeSet<Trade>> getBids() {
        return orderBook.getBids();
    }

    public synchronized Map<Integer, TreeSet<Trade>> getAsks() {
        return orderBook.getAsks();
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

    public double[] getQuantitiesDataSet() {
        return quantitiesDataSet.stream().mapToDouble(e -> e).toArray();
    }

    private double getIncline(double price) {
        double marketPrice = Tickers.getPrice(symbol);
        double ratio = price / marketPrice;
        return (ratio - 1) * 100;
    }
}
