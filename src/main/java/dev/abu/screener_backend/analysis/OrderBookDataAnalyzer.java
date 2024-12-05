package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.Tickers;
import dev.abu.screener_backend.entity.Ticker;
import dev.abu.screener_backend.entity.Trade;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static dev.abu.screener_backend.analysis.DensityAnalyzer.getDensityAnalyzer;
import static java.lang.Math.abs;

@Slf4j
public class OrderBookDataAnalyzer {

    private static final Map<String, OrderBookDataAnalyzer> obdaMap = new HashMap<>();

    private final Ticker ticker;
    private final double range;
    @Getter
    private final TreeSet<Trade> bids;
    @Getter
    private final TreeSet<Trade> asks;
    private double[] quantitiesDataSet;
    private final DensityAnalyzer densityAnalyzer;

    public static OrderBookDataAnalyzer getInstance(String symbol) {
        if (!obdaMap.containsKey(symbol)) {
            obdaMap.put(symbol, new OrderBookDataAnalyzer(new Ticker(symbol), 30));
        }
        return obdaMap.get(symbol);
    }

    /**
     * Constructor for {@code OrderBookDataAnalyzer}.
     * @param ticker ticker to analyze data for.
     * @param range percentage range used to filter price value. If -1 is selected, then all ranges will be accepted.
     */
    public OrderBookDataAnalyzer(Ticker ticker, double range) {
        this.ticker = ticker;
        this.range = range;
        this.densityAnalyzer = getDensityAnalyzer(this.ticker);
        Comparator<Trade> sortByQuantity = Comparator.comparingDouble(Trade::getQuantity).reversed();
        this.bids = new TreeSet<>(sortByQuantity);
        this.asks = new TreeSet<>(sortByQuantity);
    }

    public OrderBookDataAnalyzer(Ticker ticker, String range) {
        this(ticker, range == null ? 10 : Double.parseDouble(range));
    }

    public String getJsonOrderBookData() {
        String bidsString = bids.toString();
        String asksString = asks.toString();

        bidsString = bidsString.replace("{", "[").replace("}", "]");
        asksString = asksString.replace("{", "[").replace("}", "]");

        return String.format("{\"b\": %s, \"a\": %s}", bidsString, asksString);
    }

    public boolean processData(String orderBookDataPayload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(orderBookDataPayload);
            processData(jsonNode.get("bids"), bids, false);
            processData(jsonNode.get("asks"), asks, true);
            densityAnalyzer.setLevels(quantitiesDataSet);
            setDensities(bids, asks);
            return true;
        } catch (Exception e) {
            log.error("Error reading order book payload", e);
        }
        return false;
    }

    private void processData(JsonNode tradesArray, TreeSet<Trade> trades, boolean isAsk) {
        if (tradesArray == null) return;
        quantitiesDataSet = new double[tradesArray.size()];
        int i = 0;
        for (JsonNode trade : tradesArray) {
            double price = trade.get(0).asDouble();
            double incline = getIncline(price);
            if (inclineIsTooBig(incline)) continue;
            double quantity = trade.get(1).asDouble();
            quantitiesDataSet[i] = quantity;
            trades.add(new Trade(price, quantity, roundDown(incline), 0, isAsk, ticker.getSymbol()));
            i++;
        }
        quantitiesDataSet = Arrays.copyOf(quantitiesDataSet, i);
    }

    private void setDensities(TreeSet<Trade> bids, TreeSet<Trade> asks) {
        for (Trade bid : bids) {
            bid.setDensity(densityAnalyzer.getDensity(bid.getQuantity()));
        }
        for (Trade ask : asks) {
            ask.setDensity( densityAnalyzer.getDensity(ask.getQuantity()) );
        }
    }

    private boolean inclineIsTooBig(double incline) {
        return range != -1 && incline > range;
    }

    private double getIncline(double price) {
        double marketPrice = Tickers.getPrice(ticker.getSymbol().toLowerCase());
        double ratio = price / marketPrice;
        return abs(1 - ratio) * 100;
    }

    private double roundDown(double number) {
        NumberFormat formatter = new DecimalFormat("#0.00");
        return Double.parseDouble(formatter.format(number));
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OrderBookDataAnalyzer other) {
            return ticker == other.ticker && other.range == range;
        }
        return false;
    }
}