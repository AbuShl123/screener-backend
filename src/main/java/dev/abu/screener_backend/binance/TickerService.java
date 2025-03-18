package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.entity.Ticker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.abu.screener_backend.binance.ExchangeInfoClient.getExchangeInfo;
import static dev.abu.screener_backend.binance.TickerClient.*;

@Slf4j
@Service
public class TickerService {

    /**
     * This is a hardcoded list of tickers, that don't need to be analyzed
     */
    public static final Set<String> garbageTickers = Set.of("bttcusdt", "usdcusdt", "fdusdusdt");

    /**
     * JPA Ticker Repository to interact with a Database.
     */
    private final TickerRepository tickerRepository;

    public TickerService(TickerRepository tickerRepository) {
        this.tickerRepository = tickerRepository;
        updateTickers();
    }

    /**
     * Updates the list of all tickers and their prices every hour.
     */
    @Scheduled(initialDelay = 3600000, fixedDelay = 3600000)
    public void updateTickers() {
        setPairs();
        setAllTickers();
        stabilizePairs(getAllSymbols());
    }

    /**
     * @return {@link List<String>} containing all current symbols as {@link String} objects.
     */
    public List<String> getAllSymbols() {
        var tickers = tickerRepository.findAll();
        return new ArrayList<>(tickers.stream().map(Ticker::getSymbol).toList());
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
    public void saveTicker(String symbol, double price) {
        tickerRepository.save(new Ticker(symbol, price));
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

        for (String symbol : spotSymbols) {
            if (!futSymbols.contains(symbol)) continue;
            double price = getPrice(symbol.toLowerCase());
            saveTicker(symbol, price);
        }

        log.info("Saved all {} tickers", count());
    }
}
