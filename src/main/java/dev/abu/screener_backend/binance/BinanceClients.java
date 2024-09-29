package dev.abu.screener_backend.binance;

import java.util.HashMap;
import java.util.Map;

public abstract class BinanceClients {

    /** Websocket - Binance Ticker Price clients */
    private static final Map<Ticker, WSBinanceTickerPriceClient> binanceTickerPriceClients;
    /** Rest - Binance Order Book clients */
    private static final Map<Ticker, BinanceOrderBookClient> binanceOrderBookClients;

    static {
        binanceTickerPriceClients = new HashMap<>();
        binanceOrderBookClients = new HashMap<>();
    }

    private static void addBinanceTickerPriceClient(Ticker symbol) {
        binanceTickerPriceClients.putIfAbsent(symbol, new WSBinanceTickerPriceClient(symbol));
    }

    public synchronized static WSBinanceTickerPriceClient getBinanceTickerPriceClient(Ticker symbol) {
        if (!binanceTickerPriceClients.containsKey(symbol)) {
            addBinanceTickerPriceClient(symbol);
        }
        return binanceTickerPriceClients.get(symbol);
    }

    private static void addBinanceOrderBookClient(Ticker symbol) {
        binanceOrderBookClients.putIfAbsent(symbol, new BinanceOrderBookClient(symbol));
    }

    public synchronized static BinanceOrderBookClient getBinanceOrderBookClient(Ticker symbol) {
        if (!binanceOrderBookClients.containsKey(symbol)) {
            addBinanceOrderBookClient(symbol);
        }
        return binanceOrderBookClients.get(symbol);
    }
}