package dev.abu.screener_backend.binance.entities;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
public class KlineEvent {

    @Setter
    private boolean isSpot;

    @JsonProperty("e")
    private String eventType;

    @JsonProperty("E")
    private long eventTime;

    @JsonProperty("s")
    private String symbol;

    @JsonProperty("k")
    private KlineData kline;

    public String getSymbol() {
        return symbol.toLowerCase();
    }

    public dev.abu.screener_backend.binance.entities.KlineData getKlineData() {
        var k = getKline();
        return new dev.abu.screener_backend.binance.entities.KlineData(
                k.startTime,
                k.open,
                k.high,
                k.low,
                k.close,
                k.volume,
                k.closeTime,
                k.quoteAssetVolume,
                k.numberOfTrades,
                k.takerBuyBaseAssetVolume,
                k.takerBuyQuoteAssetVolume,
                k.ignore
        );
    }

    @Getter
    public static class KlineData {

        @JsonProperty("t")
        private long startTime;

        @JsonProperty("T")
        private long closeTime;

        @JsonProperty("i")
        private String interval;

        @JsonProperty("o")
        private String open;

        @JsonProperty("c")
        private String close;

        @JsonProperty("h")
        private String high;

        @JsonProperty("l")
        private String low;

        @JsonProperty("v")
        private String volume;

        @JsonProperty("x")
        private boolean isClosed;

        @JsonProperty("q")
        private String quoteAssetVolume;

        @JsonProperty("n")
        private long numberOfTrades;

        @JsonProperty("V")
        private String takerBuyBaseAssetVolume;

        @JsonProperty("Q")
        private String takerBuyQuoteAssetVolume;

        @JsonProperty("B")
        private String ignore;
    }
}