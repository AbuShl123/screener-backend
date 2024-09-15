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

    /** Amount of largest order books to be displayed */
    private final int size;
    /** The price span to accept (in percentages) */
    private final double range;

    private final LinkedList<Trade> trades;
    private final WSBinanceTickerPriceClient priceClient;

    public OrderBookDataAnalyzer(Ticker symbol, String size, String range) {
        this.trades = new LinkedList<>();
        this.priceClient = WSBinanceClients.getBinanceTickerPriceClient(symbol);
        this.size = size == null ? 10 : Integer.parseInt(size);
        this.range = range == null ? 10 : Double.parseDouble(range);
        setTrades();
    }

    private void setTrades() {
        for (int i = 0; i < size; i++) {
            trades.add(new Trade());
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof OrderBookDataAnalyzer other) {
            return other.size == size && other.range == range;
        }
        return false;
    }

    public boolean processData(String orderBookDataPayload) {
        try {
            boolean isUpdated = false;
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(orderBookDataPayload);
            JsonNode bidsArray = jsonNode.get("bids");
            if (bidsArray == null) return false;
            for (JsonNode bid : bidsArray) {
                double price = bid.get(0).asDouble();
                double incline = getIncline(price);
                if (isPriceOutOfInterest(incline)) {
                    continue;
                }
                double quantity = bid.get(1).asDouble();
                boolean result = setLargest(price, quantity, incline);
                if (result) isUpdated = true;
            }
            return isUpdated;
        } catch (Exception e) {
            log.error("Error reading order book payload", e);
//            return "{ \"status\": \"ANALYZER_ERROR\"}, \"message\": \"Failed to process order book data: " + e.getMessage() + "\"";
        }
        return false;
    }

    private boolean setLargest(double price, double quantity, double incline) {
        var newTrade = new Trade(price, quantity, false, incline);
        for (int i = 0; i < trades.size(); i++) {
            var oldTrade = trades.get(i);
            if (oldTrade.equals(newTrade)) return false;
            if (oldTrade.quantity < newTrade.quantity) {
                trades.add(i, newTrade);
                checkSize();
                return true;
            }
        }
        return false;
    }

    private void checkSize() {
        if (trades.size() > size) {
            trades.removeLast();
        }
    }

    private boolean isPriceOutOfInterest(double incline) {
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

    public String getJsonOrderBookData() {
        return String.format("{\"b\": %s}", trades);
    }
}