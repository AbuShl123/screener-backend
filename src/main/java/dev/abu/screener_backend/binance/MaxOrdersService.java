package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.abu.screener_backend.analysis.OrderBook;
import dev.abu.screener_backend.analysis.Trade;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MaxOrdersService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Getter
    private String maxOrders;

    public void updateMaxOrders() {
        try {
            maxOrders = generateMaxOrders();
        } catch (Exception e) {
            log.error("Failed to update max orders", e);
        }
    }

    private String generateMaxOrders() {
        ArrayNode array = mapper.createArrayNode();

        for (OrderBook orderBook : OBManager.getAllOrderBooks()) {
            Trade maxAsk = orderBook.getMaxTrade(true);
            Trade maxBid = orderBook.getMaxTrade(false);
            double maxBidQty = maxBid != null ? maxBid.getQuantity() : 0;
            double maxAskQty = maxAsk != null ? maxAsk.getQuantity() : 0;
            int bidDensity = maxBid != null ? maxBid.getLevel() : 0;
            int askDensity = maxAsk != null ? maxAsk.getLevel() : 0;
            double price = TickerService.getPrice(orderBook.getMarketSymbol());

            ObjectNode obj = mapper.createObjectNode();
            obj.put("symbol", orderBook.getMarketSymbol());
            obj.put("maxBidQty", maxBidQty);
            obj.put("maxAskQty", maxAskQty);
            obj.put("density", Math.max(bidDensity, askDensity));
            obj.put("price", price);
            array.add(obj);
        }

        return array.toString();
    }
}