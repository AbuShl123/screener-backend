package dev.abu.screener_backend.binance;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
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

    private static final int SPOT_API_RATE_LIMIT = 5700;
    private static final int FUT_API_RATE_LIMIT = 2300;

    private static final CloseableHttpClient httpClient;

    private static final AtomicInteger usedWeight1mSpot = new AtomicInteger(0);
    private static final AtomicInteger usedWeight1mFut = new AtomicInteger(0);

    private static final ReentrantLock spotLock = new ReentrantLock();
    private static final ReentrantLock futLock = new ReentrantLock();

    static {
        RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .setSocketTimeout(5000)
                .build();
        httpClient = HttpClients.custom()
                .setDefaultRequestConfig(config)
                .build();
    }

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
            int limit = isSpot ? 10_000 : 1000;
            HttpGet depthRequest = new HttpGet(baseUri + "/depth?symbol=" + symbol.toUpperCase() + "&limit=" + limit);
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
                } else return null;

            } catch (Exception e) {
                throw new RuntimeException("Failed to send request for depth snapshot: " + e.getMessage(), e);
            }

        } finally {
            lock.unlock();
        }
    }

    /**
     * Function that checks if the used API weight is close to Binance's rate limit.
     * If the used weight is close to the limit, thread sleeps until the next minute.
     * @param isSpot boolean to specify the market, true=spot false=futures.
     */
    private static void checkRateLimits(boolean isSpot) {
        int weightUsed = isSpot ? usedWeight1mSpot.get() : usedWeight1mFut.get();
        if (isApiLimitExceeded(isSpot)) {
            long millisToWait = getMillisUntilNextMinute();
            log.info("Request weight is {}. Waiting for {} seconds", weightUsed, millisToWait / 1000);
            waitFor(millisToWait);
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

    /**
     * @param isSpot boolean specifying market type (spot/perp).
     * @return true is binance api rate limit is exceeded.
     */
    private static boolean isApiLimitExceeded(boolean isSpot) {
        int apiRateLimit = isSpot ? SPOT_API_RATE_LIMIT : FUT_API_RATE_LIMIT;
        int weightUsed = isSpot ? usedWeight1mSpot.get() : usedWeight1mFut.get();
        return weightUsed >= apiRateLimit;
    }

    /**
     * Simply waits for the specified amount of time.
     * @param millis milliseconds to wait for.
     */
    private static void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Thread interrupted during rate limit wait: {}", e.getMessage());
        }
    }

//    public static void main(String[] args) {
//        Thread spotThread = new Thread(() -> {
//            log.info("Spot starting");
//            long startTime = System.currentTimeMillis();
//            int count = 0;
//            while (true) {
//                getInitialSnapshot("btcusdt", true);
//                count++;
//                if (System.currentTimeMillis() - startTime > 60_000) {
//                    log.info("Spot received {} snapshots", count);
//                    startTime = System.currentTimeMillis();
//                    count = 0;
//                }
//            }
//        });
//        Thread futThread = new Thread(() -> {
//            log.info("Futures starting");
//            long startTime = System.currentTimeMillis();
//            int count = 0;
//            while (true) {
//                getInitialSnapshot("btcusdt", false);
//                count++;
//                if (System.currentTimeMillis() - startTime > 60_000) {
//                    log.info("Futures received {} snapshots", count);
//                    startTime = System.currentTimeMillis();
//                    count = 0;
//                }
//            }
//        });
//        spotThread.start();
//        futThread.start();
//    }
}
