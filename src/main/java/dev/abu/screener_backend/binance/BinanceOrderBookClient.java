package dev.abu.screener_backend.binance;

import io.restassured.RestAssured;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

public class BinanceOrderBookClient {

    private final RequestSpecification request;

    static {
        RestAssured.baseURI = "https://api.binance.com";
        RestAssured.basePath = "/api/v3/depth";
    }

    public BinanceOrderBookClient(Ticker symbol) {
        this.request =
                given()
                        .param("symbol", symbol.name())
                        .param("limit", 1000);
    }

    public String getData() {
        return request.get()
                .then().extract().response().asPrettyString();
    }
}
