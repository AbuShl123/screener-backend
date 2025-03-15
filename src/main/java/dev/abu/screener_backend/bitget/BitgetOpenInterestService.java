package dev.abu.screener_backend.bitget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.handlers.WSOpenInterestHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;

import static java.lang.Double.NaN;

@Slf4j
@Service
public class BitgetOpenInterestService {

    private static LinkedList<String> history = new LinkedList<>();
    private static final long UPDATE_INTERVAL = 2 * 60 * 1000;
    private static final double INTEREST_THRESHOLD = 5.00;
    private static final String BITGET_URL = "https://api.bitget.com/api/mix/v1/market";
    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    private final WSOpenInterestHandler websocket;
    private final ObjectMapper mapper;
    private final Set<String> symbols;
    private final Map<String, Double> pastInterests;
    private long lastTickerUpdate;

    public BitgetOpenInterestService(WSOpenInterestHandler websocket) {
        this.websocket = websocket;
        mapper = new ObjectMapper();
        symbols = new HashSet<>();
        pastInterests = new HashMap<>();
        setAllSymbols();
        lastTickerUpdate = System.currentTimeMillis();
        symbols.forEach(symbol -> pastInterests.put(symbol, null));
    }

    @Scheduled(fixedDelay = UPDATE_INTERVAL, initialDelay = 5000)
    private void checkInterestChange() {
        updateAllTickers();
        analyzeOI();
    }

    public String getHistoricalOI() {
        StringBuilder sb = new StringBuilder("[");
        for (String data : history) {
            sb.append(data).append(",");
        }
        if (sb.charAt(sb.length() - 1) == ',') sb.deleteCharAt(sb.length() - 1);
        sb.append("]");
        return sb.toString();
    }

    private void updateAllTickers() {
        if (System.currentTimeMillis() - lastTickerUpdate >= 24 * 60 * 60 * 1000) {
            setAllSymbols();
            lastTickerUpdate = System.currentTimeMillis();
        }
    }

    private void analyzeOI() {
        for (String symbol : symbols) {
            // get current OI for the symbol
            Double currentInterest = fetchOpenInterest(symbol);
            if (currentInterest == null) continue;

            // get previous OI for the symbol
            Double pastInterest = pastInterests.get(symbol);
            if (pastInterest == null) {
                pastInterests.put(symbol, currentInterest); // this happens only once ideally in the very beginning.
                continue;
            }

            // calculate how much the OI dropped/rose from the last time
            double deltaPercentage = ((currentInterest - pastInterest) / pastInterest) * 100;

            // if the delta to above the 5%, then broadcast it.
            if (deltaPercentage > INTEREST_THRESHOLD) {
                broadcastData(deltaPercentage, currentInterest, pastInterest, symbol);
            }

            // update the past interest with the current OI
            pastInterests.put(symbol, currentInterest);
        }
    }

    private void broadcastData(double deltaPercentage, Double currentInterest, Double pastInterest, String symbol) {
        double deltaCoins = currentInterest - pastInterest;
        String symbolName = symbol.replace("_UMCBL", "");
        long timestamp = System.currentTimeMillis();
        Double price = fetchTickerPrice(symbol);
        double deltaDollars = price == null ? NaN : deltaCoins * price;
        String payload = """
                {
                "symbol": "%s",
                "deltaPercentage": %.2f,
                "deltaCoins": %f,
                "deltaDollars": %f,
                "timestamp": %d
                }
                """;
        String data = String.format(payload, symbolName, deltaPercentage, deltaCoins, deltaDollars, timestamp);
        websocket.broadCastData(data);
        appendNewEventToHistory(data);
    }

    private void appendNewEventToHistory(String event) {
        history.addLast(event);
        if (history.size() > 15) {
            history.removeFirst();
        }
    }

    public Double fetchOpenInterest(String symbol) {
        String payload = null;
        try {
            payload = getOpenInterest(symbol);
            JsonNode json = mapper.readTree(payload);
            JsonNode data = json.get("data");
            if (data == null) return null;
            return data.get("amount").asDouble();
        } catch (Exception e) {
            if (payload != null && payload.contains("The symbol has been removed")) {
                symbols.remove(symbol);
            } else if (payload != null && payload.contains("Parameter " + symbol.toUpperCase() + " cannot be empty")) {
                return null;
            } else {
                log.error("Failed to fetch open interest data - {} ", payload, e);
            }
            return null;
        }
    }

    public Double fetchTickerPrice(String symbol) {
        try {
            String payload = getPrice(symbol);
            JsonNode json = mapper.readTree(payload);
            JsonNode data = json.get("data");
            if (data == null) return null;
            return data.get("last").asDouble();
        } catch (Exception e) {
            log.error("Failed to fetch ticker price data", e);
            return null;
        }
    }

    private void setAllSymbols() {
        try {
            String payload = getAllFuturesSymbols();
            JsonNode json = mapper.readTree(payload);
            JsonNode data = json.get("data");

            if (data == null || !data.isArray()) {
                log.error("Failed to read json: {}", payload);
                return;
            }

            for (JsonNode obj : data) {
                String symbol = obj.get("symbol").asText();
                symbols.add(symbol);
            }

            log.info("All {} Bitget symbols are set.", symbols.size());
        } catch (Exception e) {
            log.error("Failed to load all Bitget symbols", e);
        }
    }

    public synchronized String getOpenInterest(String symbol) {
        String uri = BITGET_URL + "/open-interest?symbol=" + symbol;
        HttpGet request = new HttpGet(uri);
        request.addHeader("Accept", "application/json");

        try (var response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch BitGet open interest for " + symbol + ": " + e.getMessage());
        }

        return null;
    }

    public synchronized String getAllFuturesSymbols() {
        HttpGet request = new HttpGet(BITGET_URL + "/contracts?productType=umcbl");
        request.addHeader("Accept", "application/json");

        try (var response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch BitGet futures symbols: " + e.getMessage());
        }

        return null;
    }

    public synchronized String getPrice(String symbol) {
        HttpGet request = new HttpGet(BITGET_URL + "/ticker?symbol=" + symbol);
        request.addHeader("Accept", "application/json");

        try (var response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to fetch BitGet ticker price for " + symbol + ": " + e.getMessage());
        }

        return null;
    }
}
