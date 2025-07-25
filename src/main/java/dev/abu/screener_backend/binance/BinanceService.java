package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.OrderBook;
import dev.abu.screener_backend.analysis.TradeListDTO;
import dev.abu.screener_backend.binance.depth.WSFutDepthClient;
import dev.abu.screener_backend.binance.depth.WSSpotDepthClient;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.OutputStream;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static dev.abu.screener_backend.binance.OBService.printReSyncMap;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceService {

    private final TickerService tickerService;
    private final OBService obService;
    private final WSSpotDepthClient spotDepthClient;
    private final WSFutDepthClient futDepthClient;

    @PostConstruct
    public void setup() {
        spotDepthClient.startWebSocket();
        futDepthClient.startWebSocket();
        syncEveryMinute();
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void syncEveryMinute() {
        printReSyncMap();
        tickerService.syncTickerPrices();
    }

    @Scheduled(fixedDelay = 15 * 60_000)
    public void tickersUpdate() {
        if (obService.getAllSymbolDefSettings() == null) return;
        obService.truncateOrderBooks();
        tickerService.updateTickers();
        spotDepthClient.listenToSymbols(tickerService.getSpotSymbols());
        futDepthClient.listenToSymbols(tickerService.getFutSymbols());
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 180_000)
    public void sendPongMessage() {
        spotDepthClient.sendPongMessage();
        futDepthClient.sendPongMessage();
    }

    public String get5MVolumeData(String mSymbol) {
        String symbol = mSymbol.replace(FUT_SIGN, "").toUpperCase();
        boolean isSpot = !mSymbol.endsWith(FUT_SIGN);
        return BinanceClient.get5MVolumeData(symbol, isSpot);
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

    public JsonNode topOrderBook(String mSymbol) {
        return null;
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