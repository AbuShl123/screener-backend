package dev.abu.screener_backend.binance;

import lombok.extern.slf4j.Slf4j;

import static dev.abu.screener_backend.utils.EnvParams.FUT_URL;
import static dev.abu.screener_backend.utils.EnvParams.SPOT_URL;
import static io.restassured.RestAssured.given;

@Slf4j
public class ExchangeInfoClient {

    private ExchangeInfoClient() {}

    public synchronized static String getExchangeInfo(boolean isSpot) {
        String baseUri;
        if (isSpot) {
            baseUri = SPOT_URL;
        } else {
            baseUri = FUT_URL;
        }

        return
                given().when()
                        .baseUri(baseUri)
                        .get("/exchangeInfo")
                        .then()
                        .extract().response().asPrettyString();
    }
}
