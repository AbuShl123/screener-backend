package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.binance.Trade;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

import static dev.abu.screener_backend.utils.EnvParams.MAX_INCLINE;
import static java.lang.Math.abs;

@Slf4j
public class OrderBookStream {

    @Getter
    private final String symbol;
    private final TradeList orderBook;

    public OrderBookStream(String symbol) {
        this.symbol = symbol;
        this.orderBook = new TradeList(symbol);
    }

    public static synchronized OrderBookStream createInstance(String symbol) {
        return new OrderBookStream(symbol);
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
            filterByDistance(price, qty, isAsk, timestamp);
        }
    }

    private void filterByDistance(double price, double qty, boolean isAsk, long timestamp) {
        double distance = getDistance(price);
        if (abs(distance) <= MAX_INCLINE) {
            orderBook.addTrade(price, qty, distance, isAsk, timestamp);
        }
    }

    public TreeSet<Trade> getBids() {
        return orderBook.getBids();
    }

    public TreeSet<Trade> getAsks() {
        return orderBook.getAsks();
    }

    public synchronized Trade getMaxTrade(boolean isAsk) {
        return orderBook.getMaxTrade(isAsk);
    }

    private double getDistance(double price) {
        double marketPrice = TickerService.getPrice(symbol);
        double ratio = price / marketPrice;
        return (ratio - 1) * 100;
    }
}