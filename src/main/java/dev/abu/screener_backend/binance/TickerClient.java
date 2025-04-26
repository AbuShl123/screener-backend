package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.abu.screener_backend.utils.EnvParams.SPOT_URL;

@Slf4j
public class TickerClient {

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, Double> pairs = new ConcurrentHashMap<>();

    public static synchronized double getPrice(String symbol) {
        Double price = pairs.get(symbol);
        if (price == null) return -1;
        else return price;
    }

    public static boolean exists(String symbol) {
        return pairs.containsKey(symbol);
    }

    public static void setPairs() {
        try {
            String json = getData();
            List<Map<String, String>> dataList = mapper.readValue(json, new TypeReference<>() {});

            for (Map<String, String> entry : dataList) {
                pairs.put(entry.get("symbol").toLowerCase(), Double.parseDouble(entry.get("price")));
            }

        } catch (JsonProcessingException e) {
            log.error("Error while reading ticker prices", e);
        }
    }

    public static void stabilizePairs(Set<String> symbols) {
        pairs.keySet().retainAll(symbols);
    }

    private static String getData() {
        HttpGet tickerRequest = new HttpGet(SPOT_URL + "/ticker/price");
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
}
