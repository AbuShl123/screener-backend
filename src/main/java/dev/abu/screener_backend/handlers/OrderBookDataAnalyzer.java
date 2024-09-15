package dev.abu.screener_backend.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.ws.WSBinanceClients;
import dev.abu.screener_backend.binance.ws.WSBinanceTickerPriceClient;
import dev.abu.screener_backend.binance.Ticker;
import lombok.extern.slf4j.Slf4j;

import java.util.LinkedList;

import static java.lang.Math.abs;

@Slf4j
public class OrderBookDataAnalyzer {

    /** Amount of largest order books to be displayed */
    private final int DATA_SIZE;
    /** The price span to accept (in percentages) */
    private final double PRICE_SPAN;

    private final LinkedList<Trade> trades;
    private final WSBinanceTickerPriceClient priceClient;

    public OrderBookDataAnalyzer(Ticker symbol, String dataSize, String priceSpan) {
        this.trades = new LinkedList<>();
        this.priceClient = WSBinanceClients.getBinanceTickerPriceClient(symbol);
        DATA_SIZE = dataSize == null ? 10 : Integer.parseInt(dataSize);
        PRICE_SPAN = priceSpan == null ? 10 : Double.parseDouble(priceSpan);
        setTrades();
    }

    private void setTrades() {
        for (int i = 0; i < DATA_SIZE; i++) {
            trades.add(new Trade());
        }
    }

    public String processData(String orderBookDataPayload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(orderBookDataPayload);
            JsonNode bidsArray = jsonNode.get("bids");
            if (bidsArray == null) return getJsonOrderBookData();
            for (JsonNode bid : bidsArray) {
                double price = bid.get(0).asDouble();
                double incline = getIncline(price);
                if (isPriceOutOfInterest(incline)) {
                    continue;
                }
                double quantity = bid.get(1).asDouble();
                setLargest(price, quantity, incline);
            }
            return getJsonOrderBookData();
        } catch (Exception e) {
            log.error("Error reading order book payload", e);
            return "{ \"status\": \"ANALYZER_ERROR\"}, \"message\": \"Failed to process order book data: " + e.getMessage() + "\"";
        }
    }

    private void setLargest(double price, double quantity, double incline) {
        var newTrade = new Trade(price, quantity, false, incline);
        for (int i = 0; i < trades.size(); i++) {
            var oldTrade = trades.get(i);
            if (oldTrade.equals(newTrade)) return;
            if (oldTrade.volume < newTrade.volume) {
                trades.add(i, newTrade);
                checkSize();
                return;
            }
        }
    }

    private void checkSize() {
        if (trades.size() > DATA_SIZE) {
            trades.removeLast();
        }
    }

    private boolean isPriceOutOfInterest(double incline) {
        return PRICE_SPAN != -1 && incline > PRICE_SPAN;
    }

    private double getIncline(double price) {
        double marketPrice = priceClient.getPrice();
        double ratio = price / marketPrice;
        return abs(1 - ratio) * 100;
    }

    private String getJsonOrderBookData() {
        return String.format("{\"b\": %s}", trades);
    }
}