package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.analysis.OrderBookStream;
import dev.abu.screener_backend.entity.Trade;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Getter
@Slf4j
@Component
public class MaxOrdersService {
    private String maxOrders;

    @Scheduled(fixedRate = 60_000, initialDelay = 10_000)
    public void updateMaxOrders() {
        try {
            maxOrders = getData();
        } catch (Exception e) {
            log.error("Failed to update max orders", e);
        }
    }

    private String getOnlyDensityData() {
        StringBuilder data = new StringBuilder("{");

        for (OrderBookStream stream : OrderBookStream.getAllInstances()) {
            data.append("\"").append(stream.getSymbol()).append("\":").append(stream.getMaxDensity()).append(',');
        }

        if (data.charAt(data.length() - 1) == ',') data.deleteCharAt(data.length() - 1);
        data.append("}");
        return data.toString();
    }

    private String getData() {
        StringBuilder array = new StringBuilder("[");

        for (OrderBookStream stream : OrderBookStream.getAllInstances()) {
            Trade maxAsk = stream.getMaxTrade(true);
            Trade maxBid = stream.getMaxTrade(false);
            double maxBidQty = maxBid != null ? maxBid.getQuantity() : 0;
            double maxAskQty = maxAsk != null ? maxAsk.getQuantity() : 0;
            int bidDensity = maxBid != null ? maxBid.getDensity() : 0;
            int askDensity = maxAsk != null ? maxAsk.getDensity() : 0;
            double price = TickerClient.getPrice(stream.getSymbol().replace(FUT_SIGN, ""));

            array
                    .append('{')
                    .append("\"symbol\":\"").append(stream.getSymbol()).append("\",")
                    .append("\"maxBidQty\":").append(maxBidQty).append(",")
                    .append("\"maxAskQty\":").append(maxAskQty).append(",")
                    .append("\"density\":").append(Math.max(bidDensity, askDensity)).append(",")
                    .append("\"price\":").append(price)
                    .append("},");
        }

        if (array.charAt(array.length() - 1) == ',') array.deleteCharAt(array.length() - 1);
        array.append("]");
        return array.toString();
    }
}