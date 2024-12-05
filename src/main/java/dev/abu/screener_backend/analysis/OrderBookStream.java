package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.Tickers;
import dev.abu.screener_backend.binance.jpa.OrderBookService;
import dev.abu.screener_backend.entity.Trade;
import dev.abu.screener_backend.handlers.WSOrderBookHandler;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.*;
import java.util.concurrent.*;

import static java.lang.Math.abs;

@Slf4j
public class OrderBookStream {

    private static final Map<String, OrderBookStream> streams = new HashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * The maximum number of bids & asks to display per cup.
     */
    private static final int CUP_SIZE = 10;
    /**
     * The maximum incline from the market price to accept.
     */
    private static final double MAX_INCLINE = 10;
    private static final int MAX_PAIRS = 10;
    private static final int UPDATE_INTERVAL = 10_000; // in millis

    private final ConcurrentHashMap<Double, Double> bids = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Double, Double> asks = new ConcurrentHashMap<>();
    private final String symbol;
    @Setter
    private OrderBookService orderBookService;
    @Setter
    private WSOrderBookHandler websocketHandler;
    private final HashSet<Double> quantitiesDataSet = new HashSet<>();

    public OrderBookStream(String symbol) {
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
        executorService.scheduleAtFixedRate(this::check, UPDATE_INTERVAL, UPDATE_INTERVAL, TimeUnit.MILLISECONDS);
        this.symbol = symbol;
    }

    public static synchronized OrderBookStream getInstance(String ticker) {
        ticker = ticker.toLowerCase();
        if (!streams.containsKey(ticker)) {
            streams.put(ticker, new OrderBookStream(ticker));
        }
        return streams.get(ticker);
    }

    public synchronized void buffer(String rawData) {
        try {
            JsonNode json = mapper.readTree(rawData);

            var asksArray = getTradeArray(rawData, json, true);
            traverseArray(asksArray, true);

            var bidsArray = getTradeArray(rawData, json, false);
            traverseArray(bidsArray, false);

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

    private void traverseArray(JsonNode array, boolean isAsk) {
        if (array == null || (array.isArray() && array.isEmpty())) {
            return;
        }
        for (JsonNode node : array) {
            var price = node.get(0).asDouble();
            var qty = node.get(1).asDouble();
            if (isAsk) {
                filterByRange(asks, price, qty);
            } else {
                filterByRange(bids, price, qty);
            }
        }
    }

    private void filterByRange(Map<Double, Double> tradeMap, double price, double qty) {
        if (!isInclineTooBig(price)) {
            savePair(tradeMap, price, qty);
            saveQuantity(qty);
        }
    }

    private void savePair(Map<Double, Double> tradeMap, double price, double qty) {
        if (qty == 0) {
            tradeMap.remove(price);
        } else {
            tradeMap.put(price, qty);
        }
    }

    private void saveQuantity(double qty) {
        quantitiesDataSet.add(qty);
    }

    private double[] getQuantitiesDataSet() {
        return quantitiesDataSet.stream().mapToDouble(e -> e).toArray();
    }

    public void check() {
        var sortedAsks = getSortedEntries(asks);
//        asks.clear();
//        sortedAsks.forEach(entry -> asks.put(entry.getKey(), entry.getValue()));
//        orderBookService.deleteAllTradesWithSymbol(symbol);
//        orderBookService.saveTrades(asks, true, symbol);

        var sortedBids = getSortedEntries(bids);
//        bids.clear();
//        sortedBids.forEach(entry -> bids.put(entry.getKey(), entry.getValue()));
//        orderBookService.saveTrades(bids, false, symbol);

        List<Trade> bidsList = new ArrayList<>();
        sortedBids.forEach(
                entry -> {
                    Trade trade = Trade.builder()
                            .price(entry.getKey())
                            .quantity(entry.getValue())
                            .isAsk(false)
                            .incline(getIncline(entry.getKey()))
                            .build();
                    bidsList.add(trade);
                }
        );

        List<Trade> askList = new ArrayList<>();
        sortedAsks.forEach(
                entry -> {
                    Trade trade = Trade.builder()
                            .price(entry.getKey())
                            .quantity(entry.getValue())
                            .isAsk(true)
                            .incline(getIncline(entry.getKey()))
                            .build();
                    askList.add(trade);
                }
        );

        String orderbook = String.format("{\"symbol\": \"%s\", \"b\": %s, \"a\": %s}", symbol, bidsList, askList);
        websocketHandler.broadcastOrderBook(orderbook);
    }

    private List<Map.Entry<Double, Double>> getSortedEntries(Map<Double, Double> map) {
        return map
                .entrySet()
                .stream()
                .sorted(Comparator.comparingDouble(Map.Entry::getValue))
                .limit(OrderBookStream.MAX_PAIRS)
                .toList();
    }

    private boolean isInclineTooBig(double price) {
        return getIncline(price) > MAX_INCLINE;
    }

    private double getIncline(double price) {
        double marketPrice = Tickers.getPrice(symbol);
        double ratio = price / marketPrice;
        return abs(1 - ratio) * 100;
    }
}
