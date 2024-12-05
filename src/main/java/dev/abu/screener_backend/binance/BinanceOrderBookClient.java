package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.entity.Ticker;
import lombok.extern.slf4j.Slf4j;

import static io.restassured.RestAssured.given;

@Slf4j
public class BinanceOrderBookClient extends BinanceClient {

    public synchronized static String getOrderBook(Ticker ticker) {
        return
                given()
                        .param("symbol", ticker.getSymbol().toUpperCase())
                        .param("limit", 1000)
                        .when()
                        .get("/depth")
                        .then()
                        .extract().response().asPrettyString();
    }
}
