package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.abu.screener_backend.analysis.OrderBook;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.TreeSet;

@Slf4j
@Component
public class MaxOrdersService {

    private final ObjectMapper mapper = new ObjectMapper();

    @Getter
    private String maxOrders;
    @Getter
    private String depthData;

    public void updateMaxOrders() {
        try {
            maxOrders = generateMaxOrders();
        } catch (Exception e) {
            log.error("Failed to update max orders", e);
        }
    }

    public void updateDepthData() {
        try {
            depthData = generateDepthData();
        } catch (Exception e) {
            log.error("Failed to update depth data", e);
        }
    }

    private String generateMaxOrders() {
        ArrayNode array = mapper.createArrayNode();
        String jsonString = "[]";

        for (OrderBook orderBook : OBManager.getAllOrderBooks()) {
            Trade maxAsk = orderBook.getMaxTrade(true);
            Trade maxBid = orderBook.getMaxTrade(false);
            double maxBidQty = maxBid != null ? maxBid.getQuantity() : 0;
            double maxAskQty = maxAsk != null ? maxAsk.getQuantity() : 0;
            int bidDensity = maxBid != null ? maxBid.getLevel() : 0;
            int askDensity = maxAsk != null ? maxAsk.getLevel() : 0;
            double price = TickerClient.getPrice(orderBook.getMarketSymbol());

            ObjectNode obj = mapper.createObjectNode();
            obj.put("symbol", orderBook.getMarketSymbol());
            obj.put("maxBidQty", maxBidQty);
            obj.put("maxAskQty", maxAskQty);
            obj.put("density", Math.max(bidDensity, askDensity));
            obj.put("price", price);
            array.add(obj);
        }

        try {
            jsonString = mapper.writeValueAsString(array);
        } catch (Exception e) {
            log.error("Failed to update max orders", e);
        }

        return jsonString;
    }

    private String generateDepthData() {
        ArrayNode arrayNode = mapper.createArrayNode();

        for (OrderBook orderbook : OBManager.getAllOrderBooks()) {
            ObjectNode obj = mapper.createObjectNode();
            TreeSet<Trade> bids = orderbook.getBids();
            TreeSet<Trade> asks = orderbook.getAsks();
            var symbol = orderbook.getMarketSymbol();
            double price = TickerClient.getPrice(orderbook.getMarketSymbol());
            obj.put("symbol", symbol);
            obj.put("price", price);
            obj.set("bids", serializeTrades(bids));
            obj.set("asks", serializeTrades(asks));
            arrayNode.add(obj);
        }

        String jsonString = "[]";
        try {
            jsonString = mapper.writeValueAsString(arrayNode);
        } catch (Exception e) {
            log.error("Failed to serialize order books", e);
        }
        return jsonString;
    }

    private ArrayNode serializeTrades(TreeSet<Trade> trades) {
        ArrayNode outerArray = mapper.createArrayNode();
        for (Trade trade : trades) {
            ArrayNode innerArray = mapper.createArrayNode();
            innerArray.add(trade.getPrice());
            innerArray.add(trade.getQuantity());
            innerArray.add(trade.getDistance());
            innerArray.add(trade.getLevel());
            outerArray.add(innerArray);
        }
        return outerArray;
    }
}