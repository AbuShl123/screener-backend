package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.jpa.TickerService;
import dev.abu.screener_backend.entity.Ticker;
import dev.abu.screener_backend.rabbitmq.RabbitMQService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.*;

import static dev.abu.screener_backend.binance.BinanceExchangeInfoClient.getExchangeInfo;

@Slf4j
@RequiredArgsConstructor
@Component
public class BinanceWSRunner implements CommandLineRunner {

    private static final String[] popularTickers = {
            "BNXUSDT",
            "SHIBUSDT",
            "AVAXUSDT",
            "TRXUSDT",
            "USDCUSDT",
            "ADAUSDT",
            "DOGEUSDT",
            "BNBUSDT",
            "SOLUSDT",
            "XRPUSDT",
            "ETHUSDT",
            "BTCUSDT"
    };

    private final TickerService tickerService;
    private final WSBinanceOrderBookClientFactory orderBookFactory;
    private final RabbitMQService rabbitMQService;
    private final RabbitTemplate rabbitTemplate;
    private final List<String> depths = new ArrayList<>();

    @Override
    public void run(String... args) {
        setAllTickers();
        setTickerPrices();
        startOrderBook();
        setRabbitMQServices();
        sendHistoricalData();
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

        List<String> tickers = new ArrayList<>(tickerService.getAllSymbols());
        for (String popularTicker : popularTickers) {
            tickers.remove(popularTicker.toLowerCase());
            tickers.add(0, popularTicker.toLowerCase());
        }

        for (int i = 0; i < tickers.size(); i += chunkSize) {
            var chunk = tickers.subList(i, Math.min(i + chunkSize, tickers.size()));
            var symbols = chunk.toArray(new String[0]);
            String queue = "depth_" + i;
            depths.add(queue);
            orderBookFactory.createClient(queue, symbols);
        }
    }

    private void sendHistoricalData() {
        List<String> symbols = new ArrayList<>(tickerService.getAllSymbols());
        for (String popularTicker : popularTickers) {
            symbols.remove(popularTicker.toLowerCase());
            symbols.add(0, popularTicker.toLowerCase());
        }
        String depthHistQueue = "depth_historical";
        rabbitMQService.createQueue(depthHistQueue);
        sendHistoricalData(symbols);
    }

    private void sendHistoricalData(List<String> symbols) {
        int counter = 15;
        for (int i = 0; i < symbols.size(); i++) {
            String symbol = symbols.get(i);
            if (counter == 15) {
                log.info("Currently on: {} ({})", symbol, i);
                counter = 0;
            }
            String payload = BinanceOrderBookClient.getOrderBook(symbol);
            rabbitTemplate.convertAndSend("depth_historical", new String[] {symbol, payload});
            counter++;
        }
        log.info("Success: all historical data are sent.");
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
