package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.abu.screener_backend.websockets.WSOpenInterestHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.stereotype.Service;

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static dev.abu.screener_backend.binance.ExchangeInfoClient.getExchangeInfo;
import static dev.abu.screener_backend.utils.EnvParams.*;
import static java.lang.Math.abs;

@RequiredArgsConstructor
@Slf4j
@Service
public class TickerService {

    private static final Set<String> garbageTickers = Set.of("bttcusdt", "usdcusdt", "fdusdusdt");
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, Double> tickerPriceMap = new ConcurrentHashMap<>();
    private static final LinkedList<String> history = new LinkedList<>();
    private static final Map<String, Double> previousPrices = new ConcurrentHashMap<>();

    private final TickerRepository tickerRepository;
    private final WSOpenInterestHandler oiWebsocket;

    /**
     * @param mSymbol market symbol
     * @return price for the market symbol
     */
    public synchronized static double getPrice(String mSymbol) {
        Double price = tickerPriceMap.get(mSymbol);
        if (price == null) return -1;
        return price;
    }

    /**
     * @param isSpot boolean specifying market type (spot/perp).
     * @return json string, the response from binance server.
     */
    private static String fetchTickerPrices(boolean isSpot) {
        var baseUrl = isSpot ? SPOT_URL : FUT_URL;
        HttpGet tickerRequest = new HttpGet(baseUrl + "/ticker/price");
        tickerRequest.addHeader("Accept", "application/json");

        try (var response = httpClient.execute(tickerRequest)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send request for depth snapshot: " + e.getMessage());
        }

        return null;
    }

    /**
     * function that updates the ticker->price map.
     */
    private static void updateTickerPrices() {
        try {
            // save previous prices
            previousPrices.clear();
            previousPrices.putAll(tickerPriceMap);

            // get SPOT prices
            String json = fetchTickerPrices(true);
            List<Map<String, String>> dataList = mapper.readValue(json, new TypeReference<>() {
            });
            for (Map<String, String> entry : dataList) {
                tickerPriceMap.put(entry.get("symbol").toLowerCase(), Double.parseDouble(entry.get("price")));
            }

            // get FUTURES prices
            json = fetchTickerPrices(false);
            dataList = mapper.readValue(json, new TypeReference<>() {
            });
            for (Map<String, String> entry : dataList) {
                tickerPriceMap.put(entry.get("symbol").toLowerCase() + FUT_SIGN, Double.parseDouble(entry.get("price")));
            }
        } catch (JsonProcessingException e) {
            log.error("Error while reading ticker prices", e);
        }
    }

    /**
     * Updates the list of all tickers, their prices and checks price change.
     */
    public void updateTickers() {
        updateTickerPrices();
        setAllTickers();
        checkPriceChanges();
    }

    /**
     * @return {@link List<String>} containing all current symbols as {@link String} objects.
     */
    public Set<String> getAllSymbols() {
        var tickers = tickerRepository.findAll();
        return new HashSet<>(tickers.stream().map(Ticker::getSymbol).toList());
    }

    /**
     * @return {@link List<Ticker>} containing all current symbols as {@link Ticker} objects.
     */
    public List<Ticker> getAllTickers() {
        return tickerRepository.findAll();
    }

    /**
     * @return {@link List<String>} containing all symbols in spot market as {@link String} objects.
     */
    public Set<String> getSpotSymbols() {
        var tickers = tickerRepository.findByHasSpotTrue();
        return new HashSet<>(tickers.stream().map(Ticker::getSymbol).toList());
    }

    /**
     * @return {@link List<String>} containing all symbols in perpetual market as {@link String} objects.
     */
    public Set<String> getFutSymbols() {
        var tickers = tickerRepository.findByHasFutTrue();
        return new HashSet<>(tickers.stream().map(Ticker::getSymbol).toList());
    }

    /**
     * Method to save a Ticker to database.
     *
     * @param symbol  ticker name.
     * @param price   ticker price.
     * @param hasSpot boolean identifying whether the symbol exists in spot.
     * @param hasFut  boolean identifying whether the symbol exists in perpetual.
     */
    public void saveTicker(String symbol, double price, boolean hasSpot, boolean hasFut) {
        tickerRepository.save(new Ticker(symbol, price, hasSpot, hasFut));
    }

    /**
     * Clears the ticker table fully.
     */
    public void deleteAllTickers() {
        tickerRepository.deleteAll();
    }

    /**
     * @return int, the current number of tickers stored in the Database.
     */
    public long count() {
        return tickerRepository.count();
    }

    /**
     * iterates through each symbol and if the price change is at least 5%, then broadcasts data into websocket.
     */
    private void checkPriceChanges() {
        if (previousPrices.isEmpty()) return;

        for (String symbol : getFutSymbols()) {
            Double oldPrice = previousPrices.get(symbol + FUT_SIGN);
            if (oldPrice == null) continue;

            Double currentPrice = tickerPriceMap.get(symbol + FUT_SIGN);
            if (currentPrice == null) continue;

            double deltaPercentage = ((currentPrice - oldPrice) / oldPrice) * 100;
            if (abs(deltaPercentage) >= 5) {
                broadcastPriceChange(symbol, currentPrice, deltaPercentage);
            }
        }
    }

    /**
     * Creates json string and broadcasts data into websocket.
     * @param symbol symbol to send updates for
     * @param currentPrice current price of the symbol
     * @param deltaPercentage price change in %
     */
    private void broadcastPriceChange(String symbol, double currentPrice, double deltaPercentage) {
        ObjectNode obj = mapper.createObjectNode();
        obj.put("n", "price");
        obj.put("s", symbol);
        obj.put("p", currentPrice);
        obj.put("d", Math.round(deltaPercentage * 100.0) / 100.0);
        String json = obj.toString();
        appendNewEventToHistory(json);
        oiWebsocket.broadCastData(json);
    }

    /**
     * @param event json string to save into memory.
     */
    private void appendNewEventToHistory(String event) {
        history.addLast(event);
        if (history.size() > 15) {
            history.removeFirst();
        }
    }

    /**
     * Retrieves data about all existing USDT tickers in Binance, and saves it into DB.
     */
    private void setAllTickers() {
        String spotInfo = getExchangeInfo(true);
        String futInfo = getExchangeInfo(false);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode spotJson;
        JsonNode futJson;

        try {
            spotJson = mapper.readTree(spotInfo);
            futJson = mapper.readTree(futInfo);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse exchangeInfo while setting all tickers", e);
            return;
        }

        JsonNode spotArr = spotJson.get("symbols");
        JsonNode futArr = futJson.get("symbols");

        if (!spotArr.isEmpty()) deleteAllTickers();

        Set<String> spotSymbols = new HashSet<>();
        Set<String> futSymbols = new HashSet<>();

        for (JsonNode jsonNode : spotArr) {
            String status = jsonNode.get("status").asText();
            String symbol = jsonNode.get("symbol").asText().toLowerCase();
            if (status.equals("TRADING") && symbol.endsWith("usdt") && !garbageTickers.contains(symbol)) {
                spotSymbols.add(symbol);
            }
        }

        for (JsonNode jsonNode : futArr) {
            String status = jsonNode.get("status").asText();
            String symbol = jsonNode.get("symbol").asText().toLowerCase();
            if (status.equals("TRADING") && symbol.endsWith("usdt") && !garbageTickers.contains(symbol)) {
                futSymbols.add(symbol);
            }
        }

        for (String symbol : futSymbols) {
            double price = getPrice(symbol);
            boolean hasSpot = spotSymbols.contains(symbol);
            if (!hasSpot) price = getPrice(symbol + FUT_SIGN);
            saveTicker(symbol, price, hasSpot, true);
        }

        log.info("Saved all {} tickers", count());
    }
}
