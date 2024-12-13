package dev.abu.screener_backend.binance;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalTime;

import static io.restassured.RestAssured.given;

@Slf4j
public class BinanceOrderBookClient extends BinanceClient {

    private static int weightUserPerMinute = 0;

    public synchronized static String getOrderBook(String symbol) {
        if (weightUserPerMinute >= 5850) {
            int secondsToWait = 60 - LocalTime.now().getSecond();
            log.info("Request weight is {}. Waiting for {} seconds", weightUserPerMinute, secondsToWait);
            try {
                Thread.sleep(secondsToWait * 1000L);
            } catch (InterruptedException e) {
                log.error("Thread is interrupted", e);
            }
        }

        Response response = given()
                .param("symbol", symbol.toUpperCase())
                .param("limit", 1000)
                .when()
                .get("/depth")
                .then()
                .extract().response();

        weightUserPerMinute = Integer.parseInt(response.header("x-mbx-used-weight-1m"));
        return response.getBody().asString();
    }
}
