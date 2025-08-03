package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import dev.abu.screener_backend.binance.entities.KlineData;
import dev.abu.screener_backend.binance.entities.KlineInterval;
import dev.abu.screener_backend.binance.ws.BinanceWebSocket;
import dev.abu.screener_backend.binance.ws.KlineEventConsumer;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static dev.abu.screener_backend.binance.OBService.printReSyncMap;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceService {

    private final BinanceWebSocket binanceWebSocket;
    private final TickerService tickerService;
    private final OBService obService;
    private final KlineEventConsumer klineEventConsumer;

    @PostConstruct
    public void setup() {
        syncEveryMinute();
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void syncEveryMinute() {
        printReSyncMap();
        tickerService.syncTickerPrices();
    }

    @Scheduled(initialDelay = 10 * 60_000, fixedDelay = 3 * 60_000)
    public void truncateOrderBooks() {
        obService.truncateOrderBooks();
    }

    @Scheduled(fixedDelay = 15 * 60_000)
    public void tickersUpdate() {
        if (obService.getAllSymbolDefSettings() == null) return;
        tickerService.updateTickers();

        Collection<String> spotSymbols = tickerService.getSpotSymbols();
        Collection<String> futSymbols = tickerService.getFutSymbols();

        klineEventConsumer.fetchHistoricalData(spotSymbols, true);
        klineEventConsumer.fetchHistoricalData(futSymbols, false);

        binanceWebSocket.subscribeToKline(spotSymbols, KlineInterval.MIN_5, true);
        binanceWebSocket.subscribeToKline(futSymbols, KlineInterval.MIN_5, false);

        binanceWebSocket.subscribeToDepth(spotSymbols, true);
        binanceWebSocket.subscribeToDepth(futSymbols, false);
    }

    public String getKlinesData(String mSymbol, String interval, String limit) {
        String symbol = mSymbol.replace(FUT_SIGN, "").toUpperCase();
        boolean isSpot = !mSymbol.endsWith(FUT_SIGN);
        return BinanceClient.getKlinesData(symbol, interval, limit, isSpot);
    }

    public String getGVolume() {
        return klineEventConsumer.getGVolumeJsonData();
    }

    public String getCandles(String mSymbol) throws Exception {
        return klineEventConsumer.getCandles(mSymbol);
    }

    public void depthSnapshot(HttpServletResponse response, String mSymbol) throws Exception {
        if (mSymbol == null || mSymbol.isEmpty()) return;
        OrderBook orderBook = obService.getOrderBook(mSymbol);
        Map<Double, Double> bids = new TreeMap<>(orderBook.getGeneralTradeList().getBids());
        Map<Double, Double> asks = new TreeMap<>(orderBook.getGeneralTradeList().getAsks());

        OutputStream out = response.getOutputStream();
        JsonFactory factory = new JsonFactory();
        JsonGenerator gen = factory.createGenerator(out);

        gen.writeStartObject();

        gen.writeStringField("mSymbol", mSymbol);
        gen.writeNumberField("lastUpdateId", orderBook.getLastUpdateId());
        gen.writeNumberField("price", orderBook.getGeneralTradeList().getMarketPrice());
        gen.writeNumberField("bidsSize", bids.size());
        gen.writeNumberField("asksSize", asks.size());

        writeSide(gen, "bids", bids);
        writeSide(gen, "asks", asks);

        gen.writeEndObject();
        gen.flush();
        gen.close();
    }

    private void writeSide(JsonGenerator gen, String fieldName, Map<Double, Double> side) throws Exception {
        gen.writeFieldName(fieldName);
        gen.writeStartArray();
        for (Map.Entry<Double, Double> e : side.entrySet()) {
            gen.writeStartArray();
            gen.writeNumber(e.getKey());
            gen.writeNumber(e.getValue());
            gen.writeEndArray();
        }
        gen.writeEndArray();
    }
}