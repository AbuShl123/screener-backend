package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.abu.screener_backend.binance.ExchangeInfoClient.getExchangeInfo;
import static dev.abu.screener_backend.binance.TickerClient.*;

@Slf4j
@Service
public class TickerService {

    /**
     * This is a hardcoded list of tickers, that don't need to be analyzed.
     */
    public static final Set<String> garbageTickers = Set.of("bttcusdt", "usdcusdt", "fdusdusdt");

    /**
     * JPA Ticker Repository to interact with a Database.
     */
    private final TickerRepository tickerRepository;

    public TickerService(TickerRepository tickerRepository) {
        this.tickerRepository = tickerRepository;
    }

    /**
     * Updates the list of all tickers and their prices.
     */
    public void updateTickers() {
        setPairs();
        setAllTickers();
        stabilizePairs(getAllSymbols());
    }

    /**
     * @return {@link List<String>} containing all current symbols as {@link String} objects.
     */
    public Set<String> getAllSymbols() {
        var tickers = tickerRepository.findAll();
        return new HashSet<>(tickers.stream().map(Ticker::getSymbol).toList());
    }

    /**
     * @return {@link List<Ticker>} containing all current symbols as {@link Ticker} objects.
     */
    public List<Ticker> getAllTickers() {
        return tickerRepository.findAll();
    }

    /**
     * @return {@link List<String>} containing all symbols in spot market as {@link String} objects.
     */
    public Set<String> getSpotSymbols() {
        var tickers = tickerRepository.findByHasSpotTrue();
        return new HashSet<>(tickers.stream().map(Ticker::getSymbol).toList());
    }

    /**
     * @return {@link List<String>} containing all symbols in perpetual market as {@link String} objects.
     */
    public Set<String> getFutSymbols() {
        var tickers = tickerRepository.findByHasFutTrue();
        return new HashSet<>(tickers.stream().map(Ticker::getSymbol).toList());
    }

    /**
     * Method to save a Ticker to database.
     * @param symbol ticker name.
     * @param price ticker price.
     * @param hasSpot boolean specifying whether the symbol is available in spot.
     * @param hasFut boolean specifying whether the symbol is available in perpetual.
     */
    public void saveTicker(String symbol, double price, boolean hasSpot, boolean hasFut) {
        tickerRepository.save(new Ticker(symbol, price, hasSpot, hasFut));
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

    /**
     * Retrieves data about all existing USDT tickers in Binance, and saves it into DB.
     */
    public void setAllTickers() {
        String spotInfo = getExchangeInfo(true);
        String futInfo = getExchangeInfo(false);

        ObjectMapper mapper = new ObjectMapper();
        JsonNode spotJson;
        JsonNode futJson;

        try {
            spotJson = mapper.readTree(spotInfo);
            futJson = mapper.readTree(futInfo);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse exchangeInfo while setting all tickers", e);
            return;
        }

        JsonNode spotArr = spotJson.get("symbols");
        JsonNode futArr = futJson.get("symbols");

        if (!spotArr.isEmpty()) deleteAllTickers();

        Set<String> spotSymbols = new HashSet<>();
        Set<String> futSymbols = new HashSet<>();

        for (JsonNode jsonNode : spotArr) {
            String status = jsonNode.get("status").asText();
            String symbol = jsonNode.get("symbol").asText().toLowerCase();
            if (status.equals("TRADING") && symbol.endsWith("usdt") && !garbageTickers.contains(symbol)) {
                spotSymbols.add(symbol);
            }
        }

        for (JsonNode jsonNode : futArr) {
            String status = jsonNode.get("status").asText();
            String symbol = jsonNode.get("symbol").asText().toLowerCase();
            if (status.equals("TRADING") && symbol.endsWith("usdt") && !garbageTickers.contains(symbol)) {
                futSymbols.add(symbol);
            }
        }

        for (String symbol : futSymbols) {
            double price = getPrice(symbol.toLowerCase());
            saveTicker(symbol, price, spotSymbols.contains(symbol), true);
        }

        log.info("Saved all {} tickers", count());
    }
}
