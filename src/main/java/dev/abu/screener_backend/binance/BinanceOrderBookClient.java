package dev.abu.screener_backend.binance;

import lombok.extern.slf4j.Slf4j;

import static io.restassured.RestAssured.given;

@Slf4j
public class BinanceOrderBookClient extends BinanceClient {

    public synchronized static String getOrderBook(String symbol) {
        return
                given()
                        .param("symbol", symbol.toUpperCase())
                        .param("limit", 10000)
                        .when()
                        .get("/depth")
                        .then()
                        .extract().response().asPrettyString();
    }
}
