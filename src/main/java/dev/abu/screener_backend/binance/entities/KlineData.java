package dev.abu.screener_backend.binance.entities;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@JsonFormat(shape = JsonFormat.Shape.ARRAY)
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
public class KlineData implements Comparable<KlineData> {
    private long startTime;
    private String open;
    private String high;
    private String low;
    private String close;
    private String volume;
    private long closeTime;
    private String quoteAssetVolume;
    private long numberOfTrades;
    private String takerBuyBaseAssetVolume;
    private String takerBuyQuoteAssetVolume;
    private String ignore;

    @Override
    public int compareTo(KlineData o) {
        return Long.compare(startTime, o.startTime);
    }
}