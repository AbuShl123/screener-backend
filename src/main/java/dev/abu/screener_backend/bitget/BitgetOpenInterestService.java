package dev.abu.screener_backend.bitget;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.handlers.WSOpenInterestHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static java.lang.Double.NaN;
import static java.lang.Math.abs;

@Slf4j
@Service
public class BitgetOpenInterestService {

    private static final long UPDATE_INTERVAL = 2 * 60 * 1000;
    private static final double INTEREST_THRESHOLD = 5.00;

    private final ObjectMapper mapper;
    private final Set<String> symbols;
    private final Map<String, Double> pastInterests;
    private final WSOpenInterestHandler websocket;

    public BitgetOpenInterestService(WSOpenInterestHandler websocket) {
        this.websocket = websocket;
        mapper = new ObjectMapper();
        symbols = new HashSet<>();
        pastInterests = new HashMap<>();
        setAllSymbols();
        symbols.forEach(symbol -> pastInterests.put(symbol, null));
    }

    @Scheduled(fixedRate = UPDATE_INTERVAL, initialDelay = 5_000)
    private void checkInterestChange() {
        long before = System.currentTimeMillis();
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
            if (abs(deltaPercentage) > INTEREST_THRESHOLD) {
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
                websocket.broadCastData(String.format(payload, symbolName, deltaPercentage, deltaCoins, deltaDollars, timestamp));
            }

            // update the past interest with the current OI
            pastInterests.put(symbol, currentInterest);
        }
        log.info("finished iterating oi in {} seconds", (System.currentTimeMillis() - before) / 1000);
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
            log.error("Failed to fetch open interest data - {} ", payload, e);
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

    public synchronized String getOpenInterest(String symbol) {
        return given()
                .queryParam("symbol", symbol)
                .when()
                .get("https://api.bitget.com/api/mix/v1/market/open-interest")
                .then()
                .extract().response().asPrettyString();
    }

    public synchronized String getAllFuturesSymbols() {
        return given()
                .queryParam("productType", "umcbl")
                .when()
                .get("https://api.bitget.com/api/mix/v1/market/contracts")
                .then()
                .extract().response().asPrettyString();
    }

    public synchronized String getPrice(String symbol) {
        return given()
                .queryParam("symbol", symbol)
                .when()
                .get("https://api.bitget.com/api/mix/v1/market/ticker")
                .then()
                .extract().response().asPrettyString();
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
}
