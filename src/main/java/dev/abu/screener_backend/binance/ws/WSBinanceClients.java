package dev.abu.screener_backend.binance.ws;

import dev.abu.screener_backend.binance.Ticker;

import java.util.HashMap;
import java.util.Map;

public abstract class WSBinanceClients {

    /** Storing all the Binance Ticker Price clients */
    private static final Map<Ticker, WSBinanceTickerPriceClient> binanceTickerPriceClients;

    static {
        binanceTickerPriceClients = new HashMap<>();
    }

    public synchronized static void addBinanceTickerPriceClient(Ticker symbol) {
        binanceTickerPriceClients.putIfAbsent(symbol, new WSBinanceTickerPriceClient(symbol));
    }

    public synchronized static WSBinanceTickerPriceClient getBinanceTickerPriceClient(Ticker symbol) {
        if (!binanceTickerPriceClients.containsKey(symbol)) {
            addBinanceTickerPriceClient(symbol);
        }
        return binanceTickerPriceClients.get(symbol);
    }
}