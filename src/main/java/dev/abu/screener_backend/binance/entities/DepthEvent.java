package dev.abu.screener_backend.binance.entities;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import lombok.Getter;
import lombok.Setter;

@Getter
public class DepthEvent {

    @Setter
    private boolean isSpot;

    @JsonProperty("e")
    private String eventType;

    @JsonProperty("E")
    private long eventTime;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("U")
    private long firstUpdateId;

    @JsonProperty("u")
    private long finalUpdateId;

    @JsonProperty("pu")
    @JsonAlias({"lastUpdateId"})
    private Long lastUpdateId;

    @JsonProperty("b")
    @JsonAlias({"bids"})
    @JsonDeserialize(using = PriceLevelListDeserializer.class)
    private List<PriceLevel> bids;

    @JsonProperty("a")
    @JsonAlias({"asks"})
    @JsonDeserialize(using = PriceLevelListDeserializer.class)
    private List<PriceLevel> asks;

    public String getSymbol() {
        return symbol.toLowerCase();
    }
}
