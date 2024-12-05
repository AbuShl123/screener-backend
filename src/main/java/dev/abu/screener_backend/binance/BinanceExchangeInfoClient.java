package dev.abu.screener_backend.binance;

import lombok.extern.slf4j.Slf4j;

import static io.restassured.RestAssured.given;

@Slf4j
public class BinanceExchangeInfoClient extends BinanceClient {

    private BinanceExchangeInfoClient() {}

    public synchronized static String getExchangeInfo() {
        try {
           return sendRequest();
        } catch (Exception e) {
            log.warn("Couldn't get ExchangeInfo, retrying...");
            try {
                return sendRequest();
            } catch (Exception e1) {
                log.error("Failed to get ExchangeInfo", e1);
                throw e1;
            }
        }
    }

    private static String sendRequest() {
        return
                given().when()
                        .get("/exchangeInfo")
                        .then()
                        .extract().response().asPrettyString();
    }
}
