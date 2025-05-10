package dev.abu.screener_backend.binance;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static dev.abu.screener_backend.utils.EnvParams.FUT_URL;
import static dev.abu.screener_backend.utils.EnvParams.SPOT_URL;

@Slf4j
public class DepthClient {

    private DepthClient() {}

    private static final int SPOT_API_RATE_LIMIT = 5900;
    private static final int FUT_API_RATE_LIMIT = 2300;

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    private static final AtomicInteger usedWeight1mSpot = new AtomicInteger(0);
    private static final AtomicInteger usedWeight1mFut = new AtomicInteger(0);

    private static final ReentrantLock spotLock = new ReentrantLock();
    private static final ReentrantLock futLock = new ReentrantLock();

    /**
     * Gets the Binance initial depth snapshot for a given symbol in spot/perpetual market.
     * @param symbol symbol to get depth snapshot for (note: without FUT_SIGN).
     * @param isSpot boolean to specify the market, true=spot false=futures.
     * @return depth snapshot response from Binance API.
     */
    public static String getInitialSnapshot(String symbol, boolean isSpot) {
        ReentrantLock lock = isSpot ? spotLock : futLock;
        lock.lock();
        try {
            checkRateLimits(isSpot);

            String baseUri = isSpot ? SPOT_URL : FUT_URL;
            HttpGet depthRequest = new HttpGet(baseUri + "/depth?symbol=" + symbol.toUpperCase() + "&limit=1000");
            depthRequest.addHeader("Accept", "application/json");

            try (var response = httpClient.execute(depthRequest)) {
                HttpEntity entity = response.getEntity();

                // record the current used request weight
                String xMbxUsedWeight1m = response.getFirstHeader("x-mbx-used-weight-1m").getValue();
                int usedWeight = Integer.parseInt(xMbxUsedWeight1m);
                if (isSpot) {
                    usedWeight1mSpot.set(usedWeight);
                } else {
                    usedWeight1mFut.set(usedWeight);
                }

                if (entity != null) {
                    return EntityUtils.toString(entity);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to send request for depth snapshot: " + e.getMessage(), e);
            }

        } finally {
            lock.unlock();
        }
        return null;
    }

    /**
     * Function that checks if the used API weight is close to Binance's rate limit.
     * If the used weight is close to the limit, thread sleeps until the next minute.
     * @param isSpot boolean to specify the market, true=spot false=futures.
     */
    private static void checkRateLimits(boolean isSpot) {
        int apiRateLimit = isSpot ? SPOT_API_RATE_LIMIT : FUT_API_RATE_LIMIT;
        int weightUsed = isSpot ? usedWeight1mSpot.get() : usedWeight1mFut.get();

        if (weightUsed >= apiRateLimit) {
            long millisToWait = getMillisUntilNextMinute();
            log.info("Request weight is {}. Waiting for {} seconds", weightUsed, millisToWait / 1000);

            try {
                Thread.sleep(millisToWait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Thread interrupted during rate limit wait: {}", e.getMessage());
            }

            // reset counters after sleep
            usedWeight1mSpot.set(0);
            usedWeight1mFut.set(0);
        }
    }

    /**
     * @return long, time that is left until the next minute begins (in milliseconds).
     */
    private static long getMillisUntilNextMinute() {
        long currentTimeMillis = System.currentTimeMillis();
        long millisecondsInMinute = 60_000;
        long elapsed = currentTimeMillis % millisecondsInMinute;
        long result = millisecondsInMinute - elapsed + 2000;

        // avoid waiting too long
        if (result >= 60_000) {
            return 2000;
        }
        return result;
    }
}
