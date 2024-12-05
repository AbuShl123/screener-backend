package dev.abu.screener_backend.binance.jpa;

import dev.abu.screener_backend.controllers.WSOrderBookController;
import dev.abu.screener_backend.entity.Trade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderBookService {

    private final OrderBookRepository orderBookRepository;
//    private final WSOrderBookController orderBookController;

    /**
     * Broadcasts order book updates to all connected users
     */
    public void broadcastOrderBook(String symbol, Collection<Trade> bids, Collection<Trade> asks) {
        Map<String, Object> orderBookData = new HashMap<>();
        orderBookData.put("bids", bids);
        orderBookData.put("asks", asks);

        Map<String, Object> payload = new HashMap<>();
        payload.put(symbol, orderBookData);

//        orderBookController.broadcastOrderBook(payload);
    }

    /**
     * Deletes all the trade records in the table with the provided {@code ticker} name.
     *
     * @param symbol ticker name.
     */
    public void deleteAllTradesWithSymbol(String symbol) {
        orderBookRepository.deleteBySymbol(symbol);
    }

    /**
     * Saves given trades into the table.
     * @param trades array of {@link Trade} objects to save.
     */
    public void saveTrades(Collection<Trade> trades) {
        orderBookRepository.saveAll(trades);
    }

    /**
     * Saves given trades into the table.
     * @param trades map of double-to-double objects where key is price and value is qty.
     */
    public void saveTrades(Map<Double, Double> trades, boolean isAsk, String symbol) {
        Set<Trade> set = new HashSet<>();
        for (Map.Entry<Double, Double> entry : trades.entrySet()) {
            set.add(new Trade(entry.getKey(), entry.getValue(), isAsk, symbol));
        }
        saveTrades(set);
    }
}
