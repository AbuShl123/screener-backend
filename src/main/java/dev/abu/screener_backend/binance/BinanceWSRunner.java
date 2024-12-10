package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.jpa.TickerService;
import dev.abu.screener_backend.entity.Ticker;
import dev.abu.screener_backend.rabbitmq.RabbitMQService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static dev.abu.screener_backend.binance.BinanceExchangeInfoClient.getExchangeInfo;

@Slf4j
@RequiredArgsConstructor
@Component
public class BinanceWSRunner implements CommandLineRunner {

    private final TickerService tickerService;
    private final WSBinanceOrderBookClientFactory orderBookFactory;
    private final RabbitMQService rabbitMQService;
    private final Set<String> depths = new HashSet<>();

    @Override
    public void run(String... args) {
        setAllTickers();
        setTickerPrices();
        startOrderBook();
        setRabbitMQServices();
    }

    private void setTickerPrices() {
        var set = new HashSet<>(tickerService.getAllSymbols());
        Tickers.setPrices(set);
    }

    private void setRabbitMQServices() {
        for (String depth : depths) {
            rabbitMQService.createQueue(depth);
            rabbitMQService.createBinanceConsumer(depth);
        }

        List<Ticker> allTickers = tickerService.getAllTickers();
        allTickers.forEach(ticker -> rabbitMQService.createQueue(ticker.getSymbol()));
    }

    private void startOrderBook() {
        int chunkSize = 30;
        var tickers = tickerService.getAllSymbols();
        for (int i = 0; i < tickers.size(); i += chunkSize) {
            var chunk = tickers.subList(i, Math.min(i + chunkSize, tickers.size()));
            var symbols = chunk.toArray(new String[0]);
            String queue = "depth_" + i;
            depths.add(queue);
            orderBookFactory.createClient(queue, symbols);
        }
    }

    private void setAllTickers() {
        String exchangeInfo = getExchangeInfo();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode json;

        try {
            json = mapper.readTree(exchangeInfo);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse exchangeInfo while setting all tickers", e);
            return;
        }

        JsonNode array = json.get("symbols");

        if (!array.isEmpty()) tickerService.deleteAllTickers();
        for (JsonNode jsonNode : array) {
            String status = jsonNode.get("status").asText();
            if (status.equals("TRADING")) {
                String symbol = jsonNode.get("symbol").asText().toLowerCase();
                tickerService.saveTicker(symbol);
            }
        }

        log.info("Saved all {} tickers", tickerService.count());
    }
}
