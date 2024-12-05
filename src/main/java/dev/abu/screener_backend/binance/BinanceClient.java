package dev.abu.screener_backend.binance;

import io.restassured.RestAssured;

public abstract class BinanceClient {

    static {
        RestAssured.baseURI = "https://api.binance.com/api/v3";
    }

}