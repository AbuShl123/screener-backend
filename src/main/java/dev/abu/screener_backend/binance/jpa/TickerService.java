package dev.abu.screener_backend.binance.jpa;

import dev.abu.screener_backend.entity.Ticker;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TickerService {

    /** JPA Ticker Repository to interact with a Database */
    private final TickerRepository tickerRepository;

    /**
     * @return {@link List<String>} containing all current symbols as {@link String} objects.
     */
    public List<String> getAllSymbols() {
        var tickers = tickerRepository.findAll();
        return tickers.stream().map(Ticker::getSymbol).toList();
    }

    /**
     * @return {@link List<Ticker>} containing all current symbols as {@link Ticker} objects.
     */
    public List<Ticker> getAllTickers() {
        return tickerRepository.findAll();
    }

    /**
     * @param symbol ticker to save to the Database.
     */
    public void saveTicker(String symbol) {
        tickerRepository.save(new Ticker(symbol));
    }

    /**
     * Clears the ticker table fully.
     */
    public void deleteAllTickers() {
        tickerRepository.deleteAll();
    }

    /**
     * @return int, the current number of tickers stored in the Database.
     */
    public long count() {
        return tickerRepository.count();
    }
}
