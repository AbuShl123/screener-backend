package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.entity.Ticker;
import dev.abu.screener_backend.binance.WSBinanceTickerPriceClient;
import dev.abu.screener_backend.entity.Trade;
import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;

import static dev.abu.screener_backend.analysis.DensityAnalyzer.getDensityAnalyzer;
import static dev.abu.screener_backend.binance.WSBinanceTickerPriceClient.getBinanceTickerPriceClient;
import static java.lang.Math.abs;

@Slf4j
public class OrderBookDataAnalyzer {

    /** size of the order book (always 5) */
    private final int size = 5;
    /** The price span to accept (in percentages) */
    private final double range;
    /** The symbol - ticker */
    private final Ticker symbol;

    /** Array with the largest bids */
    private final Trade[] bids = new Trade[size];
    /** Array with the largest asks */
    private final Trade[] asks = new Trade[size];
    /** Array with all the quantities */
    private double[] quantitiesDataSet;

    /** Binance Websocket to get ticker price */
    private final WSBinanceTickerPriceClient priceClient;
    /** Density analyzer */
    private final DensityAnalyzer densityAnalyzer;

    public OrderBookDataAnalyzer(Ticker symbol, String range) {
        this.symbol = symbol;
        this.priceClient = getBinanceTickerPriceClient(symbol);
        this.range = range == null ? 10 : Double.parseDouble(range);
        this.densityAnalyzer = getDensityAnalyzer(symbol);
        setTrades();
    }

    public String getJsonOrderBookData() {
        return String.format("{\"b\": %s, \"a\": %s}", Arrays.toString(bids), Arrays.toString(asks));
    }

    public boolean processData(String orderBookDataPayload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(orderBookDataPayload);
            boolean bidsUpdate = processData(jsonNode.get("bids"), bids);
            boolean asksUpdate = processData(jsonNode.get("asks"), asks);
            if (bidsUpdate || asksUpdate){
                densityAnalyzer.setLevels(quantitiesDataSet);
                setDensities(bids, asks);
                return true;
            }
        } catch (Exception e) {
            log.error("Error reading order book payload", e);
        }
        return false;
    }

    private boolean processData(JsonNode tradesArray, Trade[] trades) {
        if (tradesArray == null) return false;

        boolean result = false;
        quantitiesDataSet = new double[tradesArray.size()];
        int i = 0;

        for (JsonNode trade : tradesArray) {

            double price = trade.get(0).asDouble();
            double incline = getIncline(price);
            if (inclineIsTooBig(incline)) continue;

            double quantity = trade.get(1).asDouble();
            quantitiesDataSet[i] = quantity;

            boolean hasUpdates = setLargest(price, quantity, incline, trades);

            if ( hasUpdates ) {
                result = true;
            }

            i++;
        }

        quantitiesDataSet = Arrays.copyOf(quantitiesDataSet, i);
        return result;
    }

    private boolean setLargest(double price, double quantity, double incline, Trade[] trades) {

        Trade newTrade = new Trade(price, quantity, roundDown(incline), 0);

        for (int i = 0; i < size; i++) {

            Trade oldTrade = trades[i];

            if ( oldTrade .equals ( newTrade )) {
                return false;
            }

            if ( oldTrade.quantity < newTrade.quantity ) {

                shiftInsert(trades, newTrade, i);
                return true;

            }
        }

        return false;
    }

    private void setDensities(Trade[] bids, Trade[] asks) {
        for (int i = 0; i < size; i++) {
            bids[i].density = densityAnalyzer.getDensity(bids[i].quantity);
            asks[i].density = densityAnalyzer.getDensity(bids[i].quantity);
        }
    }

    private void setTrades() {
        for (int i = 0; i < 5; i++) {
            bids[i] = new Trade();
            asks[i] = new Trade();
        }
    }

    private boolean inclineIsTooBig(double incline) {
        return range != -1 && incline > range;
    }

    private double getIncline(double price) {
        double marketPrice = priceClient.getPrice();
        double ratio = price / marketPrice;
        return abs(1 - ratio) * 100;
    }

    private double roundDown(double number) {
        NumberFormat formatter = new DecimalFormat("#0.00");
        return Double.parseDouble(formatter.format(number));
    }

    private void shiftInsert(Trade[] trades, Trade newTrade, int index) {
        if (index < 0 || index >= trades.length) {
            log.info("Index out of bounds");
            return;
        }

        for (int i = trades.length - 1; i > index; i--) {
            trades[i] = trades[i - 1];
        }

        trades[index] = newTrade;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OrderBookDataAnalyzer other) {
            return symbol == other.symbol && other.range == range;
        }
        return false;
    }
}