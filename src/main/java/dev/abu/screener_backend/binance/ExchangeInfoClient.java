package dev.abu.screener_backend.binance;

import lombok.extern.slf4j.Slf4j;

import static io.restassured.RestAssured.given;

@Slf4j
public class ExchangeInfoClient {

    private ExchangeInfoClient() {}

    public synchronized static String getExchangeInfo(boolean isSpot) {
        String baseUri;
        if (isSpot) {
            baseUri = "https://api.binance.com/api/v3";
        } else {
            baseUri = "https://fapi.binance.com/fapi/v1";
        }

        return
                given().when()
                        .baseUri(baseUri)
                        .get("/exchangeInfo")
                        .then()
                        .extract().response().asPrettyString();
    }
}
