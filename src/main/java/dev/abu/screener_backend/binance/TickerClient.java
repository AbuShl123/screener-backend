package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;

@Slf4j
public class TickerClient extends BinanceClient {

    private static final Map<String, Double> prices = new HashMap<>();
    private static final ObjectMapper mapper = new ObjectMapper();

    private record SymbolPrice(String symbol, double price) { }

    public static void setPrices(List<String> symbols) {
        try {
            String payload = getData();
            List<SymbolPrice> pairs = mapper.readValue(payload, new TypeReference<>() {});
            for (SymbolPrice pair : pairs) {
                String symbol = pair.symbol().toLowerCase();
                if (!symbols.contains(symbol)) {
                    continue;
                }
                double price = pair.price();
                prices.put(symbol, price);
            }
            log.info("All prices for {} tickers are set successfully.", symbols.size());
        } catch (JsonProcessingException e) {
            log.error("Error while parsing JSON", e);
        }
    }

    public static synchronized double getPrice(String symbol) {
        double price = prices.getOrDefault(symbol, 0.0);
        if (price == 0.0) {
            log.error("price not set for symbol {}", symbol);
            return 1;
        }
        return price;
    }

    private static String getData() {
        return
                given().when()
                        .get("/ticker/price")
                        .then()
                        .extract().response().asPrettyString();
    }
}
