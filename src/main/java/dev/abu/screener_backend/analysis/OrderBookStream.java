package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import dev.abu.screener_backend.binance.TickerClient;
import dev.abu.screener_backend.entity.Trade;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;
import static dev.abu.screener_backend.utils.EnvParams.MAX_INCLINE;
import static java.lang.Math.abs;

@Slf4j
public class OrderBookStream {

    private static final Map<String, OrderBookStream> streams = new HashMap<>();
    @Getter
    private final String symbol;
    private final TradeList orderBook;

    public OrderBookStream(String symbol) {
        this.symbol = symbol;
        this.orderBook = new TradeList(symbol);
    }

    public static synchronized OrderBookStream createInstance(String symbol) {
        var stream = new OrderBookStream(symbol);
        streams.put(symbol, stream);
        return stream;
    }

    public static synchronized OrderBookStream getInstance(String symbol) {
        return streams.get(symbol);
    }

    public static synchronized Collection<OrderBookStream> getAllInstances() {
        return streams.values();
    }

    public void reset() {
        orderBook.clear();
    }

    public void analyze(JsonNode root, JsonNode asksArray, JsonNode bidsArray) {
        long timestamp = getTimeStamp(root);
        traverseArray(asksArray, timestamp, true);
        traverseArray(bidsArray, timestamp, false);
    }

    private long getTimeStamp(JsonNode root) {
        JsonNode eField = root.get("E");
        if (eField == null) return System.currentTimeMillis();
        long timestamp = eField.asLong();
        return timestamp == 0 ? System.currentTimeMillis() : timestamp;
    }

    private void traverseArray(JsonNode array, long timestamp, boolean isAsk) {
        if (array == null || (array.isArray() && array.isEmpty())) {
            return;
        }
        for (JsonNode node : array) {
            var price = node.get(0).asDouble();
            var qty = node.get(1).asDouble();
            filterByRange(price, qty, isAsk, timestamp);
        }
    }

    private void filterByRange(double price, double qty, boolean isAsk, long timestamp) {
        double incline = getIncline(price);
        if (abs(incline) <= MAX_INCLINE) {
            orderBook.addTrade(price, qty, incline, isAsk, timestamp);
        }
    }

    public synchronized List<Trade> getBids() {
        return orderBook.getBids().values().stream().flatMap(Set::stream).collect(Collectors.toList());
    }

    public synchronized List<Trade> getAsks() {
        return orderBook.getAsks().values().stream().flatMap(Set::stream).collect(Collectors.toList());
    }

    public synchronized Trade getMaxTrade(boolean isAsk) {
        return orderBook.getMaxTrade(isAsk);
    }

    public synchronized int getMaxDensity() {
        return orderBook.getMaxDensity();
    }

    private double getIncline(double price) {
        String ticker = symbol.replace(FUT_SIGN, "");
        double marketPrice = TickerClient.getPrice(ticker);
        double ratio = price / marketPrice;
        return (ratio - 1) * 100;
    }
}