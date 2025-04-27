package dev.abu.screener_backend.binance;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

public class SecuredEndpoint {

    public static final String BINANCE_SPOT_URI = "https://api.binance.com";
    public static final String API_KEY = "";
    public static final String SECRET_KEY = "";

    private static final CloseableHttpClient httpClient = HttpClients.createDefault();

    public static String getPayHistory(String queryParams) {
        String queryParamsWithSignature = appendSignature(queryParams);
        String uri = BINANCE_SPOT_URI + "/sapi/v1/pay/transactions?" + queryParamsWithSignature;

        HttpGet request = new HttpGet(uri);
        request.addHeader("Accept", "application/json");
        request.addHeader("Content-Type", "application/json");
        request.addHeader("X-MBX-APIKEY", API_KEY);

        try (var response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            return EntityUtils.toString(entity);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send request for depth snapshot: " + e.getMessage());
        }
    }

    private static String appendSignature(String payload) {
        return payload + "&signature=" + generateSignature(payload);
    }

    private static String generateSignature(String payload) {
        return new HmacUtils(HmacAlgorithms.HMAC_SHA_256, SECRET_KEY).hmacHex(payload);
    }
}
