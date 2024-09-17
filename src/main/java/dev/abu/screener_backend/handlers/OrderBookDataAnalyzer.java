package dev.abu.screener_backend.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.Ticker;
import dev.abu.screener_backend.binance.ws.WSBinanceClients;
import dev.abu.screener_backend.binance.ws.WSBinanceTickerPriceClient;
import dev.abu.screener_backend.entity.Trade;
import lombok.extern.slf4j.Slf4j;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedList;

import static java.lang.Math.abs;

@Slf4j
public class OrderBookDataAnalyzer {

    /** size of the order book - always 10 */
    private final int size;
    /** The price span to accept (in percentages) */
    private final double range;
    /** The symbol - ticker */
    private final Ticker symbol;

    private final LinkedList<Trade> bids = new LinkedList<>();
    private final LinkedList<Trade> asks = new LinkedList<>();
    private final WSBinanceTickerPriceClient priceClient;

    public OrderBookDataAnalyzer(Ticker symbol, String range) {
        this.symbol = symbol;
        this.priceClient = WSBinanceClients.getBinanceTickerPriceClient(symbol);
        this.size = 5;
        this.range = range == null ? 10 : Double.parseDouble(range);
        setTrades();
    }

    private void setTrades() {
        for (int i = 0; i < 5; i++) {
            bids.add(new Trade());
            asks.add(new Trade());
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OrderBookDataAnalyzer other) {
            return symbol == other.symbol && other.range == range;
        }
        return false;
    }

    public boolean processData(String orderBookDataPayload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(orderBookDataPayload);
            boolean bidsUpdate = processData(jsonNode.get("bids"), false);
            boolean asksUpdate = processData(jsonNode.get("asks"), true);
            return bidsUpdate || asksUpdate;
        } catch (Exception e) {
            log.error("Error reading order book payload", e);
        }
        return false;
    }

    private boolean processData(JsonNode tradesArray, boolean isAsk) {
        boolean result = false;
        if (tradesArray == null) return false;
        for (JsonNode trade : tradesArray) {
            double price = trade.get(0).asDouble();
            double incline = getIncline(price);
            if (inclineIsTooBig(incline)) continue;
            double quantity = trade.get(1).asDouble();
            if ( setLargest(price, quantity, incline, isAsk) ) {
                result = true;
            }
        }
        return result;
    }

    private boolean setLargest(double price, double quantity, double incline, boolean isAsk) {
        LinkedList<Trade> trades;
        if (isAsk) {
            trades = this.asks;
        } else {
            trades = this.bids;
        }

        var newTrade = new Trade(price, quantity, false, roundDown(incline));
        for (int i = 0; i < trades.size(); i++) {
            var oldTrade = trades.get(i);
            if (oldTrade.equals(newTrade)) return false;
            if (oldTrade.quantity < newTrade.quantity) {
                trades.add(i, newTrade);
                checkSize(trades);
                return true;
            }
        }
        return false;
    }

    public String getJsonOrderBookData() {
        return String.format("{\"b\": %s, \"a\": %s}", bids, asks);
    }

    private void checkSize(LinkedList<Trade> trades) {
        if (trades.size() > size) {
            trades.removeLast();
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
}