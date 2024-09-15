package dev.abu.screener_backend.binance.rest;

import dev.abu.screener_backend.binance.Ticker;
import dev.abu.screener_backend.handlers.WSOrderBookHandler;
import io.restassured.RestAssured;
import lombok.extern.slf4j.Slf4j;

import static io.restassured.RestAssured.given;

@Slf4j
public class BinanceOrderBookClient {

    private static final long DEFAULT_DELAY = 3000L;
    private final Ticker symbol;
    private boolean isOpen;

    static {
        RestAssured.baseURI = "https://api.binance.com/";
        RestAssured.basePath = "/api/v3/depth";
    }

    public BinanceOrderBookClient(Ticker symbol) {
        this.symbol = symbol;
        this.isOpen = true;
        connect();
    }

    private void connect() {
        while (isOpen) {
            String payload = given()
                    .param("symbol", symbol.name())
                    .param("limit", 1000)
                    .when()
                    .get()
                    .then().extract().response().asPrettyString();
            WSOrderBookHandler.broadCastOrderBookData(payload, symbol);
        }
    }

    private void closeConnection() {
        isOpen = false;
    }

    private static void delay() {
        try {
            Thread.sleep(DEFAULT_DELAY);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
    }
}
