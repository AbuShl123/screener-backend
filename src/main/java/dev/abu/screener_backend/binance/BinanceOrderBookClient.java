package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.entity.Ticker;
import io.restassured.RestAssured;

import static io.restassured.RestAssured.given;

public class BinanceOrderBookClient {

    static {
        RestAssured.baseURI = "https://api.binance.com";
        RestAssured.basePath = "/api/v3/depth";
    }

    public synchronized static String getOrderBook(Ticker symbol) {
        String result =
            given()
                    .param("symbol", symbol.name())
                    .param("limit", 1000)
            .when()
                    .get()
            .then()
                    .extract().response().asPrettyString();

        try {
            Thread.sleep(1000L);
        } catch (InterruptedException ignored) {}

        return result;
    }
}
