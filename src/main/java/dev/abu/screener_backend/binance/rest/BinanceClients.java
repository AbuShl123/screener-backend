package dev.abu.screener_backend.binance.rest;

import dev.abu.screener_backend.binance.Ticker;

import java.util.HashMap;
import java.util.Map;

public class BinanceClients {
    /** Storing all the Binance Order Book clients */
    private static final Map<Ticker, BinanceOrderBookClient> binanceOrderBookClients;

    static {
        binanceOrderBookClients = new HashMap<>();
    }

    public synchronized static void addBinanceOrderBookClient(Ticker symbol) {
        binanceOrderBookClients.putIfAbsent(symbol, new BinanceOrderBookClient(symbol));
    }

    public synchronized static BinanceOrderBookClient getBinanceOrderBookClient(Ticker symbol) {
        if (!binanceOrderBookClients.containsKey(symbol)) {
            addBinanceOrderBookClient(symbol);
        }
        return binanceOrderBookClients.get(symbol);
    }
}
