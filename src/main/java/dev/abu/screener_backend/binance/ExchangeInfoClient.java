package dev.abu.screener_backend.binance;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import static dev.abu.screener_backend.utils.EnvParams.FUT_URL;
import static dev.abu.screener_backend.utils.EnvParams.SPOT_URL;

@Slf4j
public class ExchangeInfoClient {

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    private ExchangeInfoClient() {}

    public synchronized static String getExchangeInfo(boolean isSpot) {
        String baseUri;
        if (isSpot) {
            baseUri = SPOT_URL;
        } else {
            baseUri = FUT_URL;
        }

        HttpGet request = new HttpGet(baseUri + "/exchangeInfo");
        request.addHeader("Accept", "application/json");

        try (var response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get send request for depth snapshot: " + e.getMessage());
        }

        log.warn("Exchange info is null");
        return null;
    }
}
