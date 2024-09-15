package dev.abu.screener_backend;

import io.restassured.RestAssured;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;

public class Main {
    public static void main(String[] args) {

        RestAssured.baseURI = "https://api.binance.com/";
        RestAssured.basePath = "/api/v3/depth";

        Response response =
                given()
                .param("symbol", "BTCUSDT")
                .param("limit", 1000)
                .when()
                .get()
                .then().extract().response();

//        response.prettyPrint();

        String payload = response.asPrettyString();
        System.out.println(payload);
    }
}
