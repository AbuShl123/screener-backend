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
public class DepthClient {

    private DepthClient() {}

    private static final int SPOT_API_RATE_LIMIT = 5900;
    private static final int FUT_API_RATE_LIMIT = 2300;

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();
    private static int weightUsedPerMinute = 0;

    public synchronized static String getInitialSnapshot(String symbol, boolean isSpot) {
        checkRateLimits(isSpot);

        String baseUri = isSpot ? SPOT_URL : FUT_URL;
        HttpGet depthRequest = new HttpGet(baseUri + "/depth?symbol=" + symbol.toUpperCase() + "&limit=1000");
        depthRequest.addHeader("Accept", "application/json");

        try (var response = httpClient.execute(depthRequest)) {
            HttpEntity entity = response.getEntity();

            // record the current used request weight
            String xMbxUsedWeight1m = response.getFirstHeader("x-mbx-used-weight-1m").getValue();
            weightUsedPerMinute = Integer.parseInt(xMbxUsedWeight1m);

            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send request for depth snapshot: " + e.getMessage());
        }
        return null;
    }

    private static void checkRateLimits(boolean isSpot) {
        // Binance has api rate limits that need to be respected or otherwise the ip will be banned
        int apiRateLimit = isSpot ? SPOT_API_RATE_LIMIT : FUT_API_RATE_LIMIT;
        if (weightUsedPerMinute >= apiRateLimit) {

            // wait until the next minute, so that the weight-used-per-minute gets reset
            long millisToWait = getMillisUntilNextMinute();
            log.info("Request weight is {}. Waiting for {} seconds", weightUsedPerMinute, millisToWait/1000);

            try {
                Thread.sleep(millisToWait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted: {}", e.getMessage());
            }

            weightUsedPerMinute = 0;
        }
    }

    private static long getMillisUntilNextMinute() {
        long currentTimeMillis = System.currentTimeMillis();

        // Calculate milliseconds left until the next minute
        long millisecondsInSecond = 1000;
        long secondsInMinute = 60;
        long millisecondsInMinute = secondsInMinute * millisecondsInSecond;

        // Calculate elapsed milliseconds within the current minute
        long elapsedMillisecondsInMinute = currentTimeMillis % millisecondsInMinute;

        // Calculate remaining milliseconds until the next minute and add 2 seconds just in case...
        long result = millisecondsInMinute - elapsedMillisecondsInMinute + 2000;

        // in case if we need to wait for 1min,
        // then it's kinda wierd, so just waiting for 2 seconds will be enough
        if (result >= 60_000) {
            return 2;
        }

        return result;
    }
}
