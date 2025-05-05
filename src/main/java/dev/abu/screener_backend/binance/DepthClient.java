package dev.abu.screener_backend.binance;

import lombok.Getter;
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
    private static int usedWeight1mSpot = 0;
	private static int usedWeight1mFut = 0;
    @Getter private static boolean isDisabled;

    /**
     * Gets the Binance initial depth snapshot for a given symbol in spot/perpetual market.
     * @param symbol symbol to get depth snapshot for (note: without FUT_SIGN).
     * @param isSpot boolean to specify the market, true=spot false=futures.
     * @return depth snapshot response from Binance API.
     */
    public synchronized static String getInitialSnapshot(String symbol, boolean isSpot) {
        checkRateLimits(isSpot);

        String baseUri = isSpot ? SPOT_URL : FUT_URL;
        HttpGet depthRequest = new HttpGet(baseUri + "/depth?symbol=" + symbol.toUpperCase() + "&limit=1000");
        depthRequest.addHeader("Accept", "application/json");

        try (var response = httpClient.execute(depthRequest)) {
            HttpEntity entity = response.getEntity();

            // record the current used request weight
            String xMbxUsedWeight1m = response.getFirstHeader("x-mbx-used-weight-1m").getValue();
            if (isSpot) usedWeight1mSpot = Integer.parseInt(xMbxUsedWeight1m);
			else usedWeight1mFut = Integer.parseInt(xMbxUsedWeight1m);

            if (entity != null) {
                return EntityUtils.toString(entity);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to send request for depth snapshot: " + e.getMessage());
        }
        return null;
    }

    /**
     * Function that checks if the used API weight is close to Binance's rate limit.
     * If the used weight is close to the limit, thread sleeps until the next minute.
     * @param isSpot boolean to specify the market, true=spot false=futures.
     */
    private static void checkRateLimits(boolean isSpot) {
        // Binance has api rate limits that need to be respected otherwise the ip will be banned
        int apiRateLimit = isSpot ? SPOT_API_RATE_LIMIT : FUT_API_RATE_LIMIT;
		int weightUsedPerMinute = isSpot ? usedWeight1mSpot : usedWeight1mFut;
		
        if (weightUsedPerMinute >= apiRateLimit) {
            long millisToWait = getMillisUntilNextMinute();
            log.info("Request weight is {}. Waiting for {} seconds", weightUsedPerMinute, millisToWait/1000);
            isDisabled = true;
            try {
                Thread.sleep(millisToWait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted: {}", e.getMessage());
            }
            usedWeight1mSpot = 0;
            usedWeight1mFut = 0;
        }
    }

    /**
     * @return long, time that is left until the next minute begins (in milliseconds).
     */
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
