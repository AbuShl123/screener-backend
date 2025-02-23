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
    private final HashSet<Double> quantitiesDataSet = new HashSet<>();
    private final DensityAnalyzer densityAnalyzer;

    public OrderBookStream(String symbol) {
        this.symbol = symbol;
        this.orderBook = new TradeList(symbol);
        this.densityAnalyzer = DensityAnalyzer.getDensityAnalyzer(symbol);
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

        densityAnalyzer.analyzeDensities(getQuantitiesDataSet());
        quantitiesDataSet.clear();
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
            var price = node.get(0).asText();
            var qty = node.get(1).asDouble();
            filterByRange(price, qty, isAsk, timestamp);
        }
    }

    private void filterByRange(String price, double qty, boolean isAsk, long timestamp) {
        double incline = getIncline(price);
        if (abs(incline) <= MAX_INCLINE) {
            orderBook.addTrade(price, qty, incline, isAsk, timestamp);
            quantitiesDataSet.add(qty);
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

    public double[] getQuantitiesDataSet() {
        return quantitiesDataSet.stream().mapToDouble(e -> e).toArray();
    }

    private double getIncline(String price) {
        double p = Double.parseDouble(price);
        String ticker = symbol.replace(FUT_SIGN, "");
        double marketPrice = TickerClient.getPrice(ticker);
        double ratio = p / marketPrice;
        return (ratio - 1) * 100;
    }
}