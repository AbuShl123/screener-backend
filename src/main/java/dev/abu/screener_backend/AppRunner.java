package dev.abu.screener_backend;

import dev.abu.screener_backend.analysis.LocalOrderBook;
import dev.abu.screener_backend.analysis.OrderBookStream;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.binance.WSDepthClient;
import dev.abu.screener_backend.entity.Ticker;
import dev.abu.screener_backend.rabbitmq.RabbitMQService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static dev.abu.screener_backend.binance.TickerClient.setPrices;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
@RequiredArgsConstructor
@Component
public class AppRunner implements CommandLineRunner {

    private static final int CHUNK_SIZE = 5;

    private final TickerService tickerService;
    private final RabbitMQService rabbitMQService;
    private final RabbitTemplate rabbitTemplate;

    @Override
    public void run(String... args) {
        setTickers();
        setRabbitMQServices();
        setDataStreams();
        startOrderBook();
    }

    private void setTickers() {
        tickerService.deleteAllTickers();
        tickerService.setAllTickers();
        setPrices(tickerService.getAllSymbols());
    }

    private void setRabbitMQServices() {
        List<Ticker> tickers = tickerService.getAllTickers();
        for (Ticker ticker : tickers) {
            String symbol = ticker.getSymbol();
            if (ticker.isHasSpot()) {
                rabbitMQService.createQueue(symbol);
            }
            if (ticker.isHasFut()) {
                rabbitMQService.createQueue(symbol + FUT_SIGN);
            }
        }
    }

    private void setDataStreams() {
        List<Ticker> tickers = tickerService.getAllTickers();
        for (Ticker ticker : tickers) {
            String symbol = ticker.getSymbol();
            String futSymbol = symbol + FUT_SIGN;
            if (ticker.isHasSpot()) {
                OrderBookStream.createInstance(symbol).setRabbitTemplate(rabbitTemplate);
                LocalOrderBook.createInstance(symbol, true);
            }
            if (ticker.isHasFut()) {
                OrderBookStream.createInstance(futSymbol).setRabbitTemplate(rabbitTemplate);
                LocalOrderBook.createInstance(futSymbol, false);
            }
        }
    }

    private void startOrderBook() {
        List<String> spotSymbols = tickerService.getSpotSymbols();
        List<String> futSymbols = tickerService.getFutSymbols();
        List<String> spotChunk = new ArrayList<>();
        List<String> futChunk = new ArrayList<>();
        int spotSize = spotSymbols.size();
        int futSize = futSymbols.size();
        int i = 1;
        int j = 1;

        for (; i <= spotSize || j <= futSize; i++, j++) {
            spotChunk.add(spotSymbols.get(i - 1));
            futChunk.add(futSymbols.get(i - 1));

            if (i <= spotSize && (i % CHUNK_SIZE == 0 || i == spotSize)) {
                String[] chunk = spotChunk.toArray(new String[0]);
                new WSDepthClient(true, "depth_" + i, chunk);
                waitForReSyncToComplete(chunk, true);
                spotChunk.clear();
                log.info("Done with spot {}", i);
            }

            if (j <= futSize && (j % CHUNK_SIZE == 0 || j == futSize)) {
                String[] chunk = futChunk.toArray(new String[0]);
                new WSDepthClient(false, "depth_" + j, chunk);
                waitForReSyncToComplete(chunk, false);
                futChunk.clear();
                log.info("Done with fut {}", j);
            }
        }

        log.info("Success all Order Books started");
    }

    private void waitForReSyncToComplete(String[] symbols, boolean isSpot) {
        for (String symbol : symbols) {
            String ticker = isSpot ? symbol : symbol + FUT_SIGN;
            LocalOrderBook orderBook = LocalOrderBook.getInstance(ticker);
            while(!orderBook.isReSyncCompleted()) {
                sleep();
            }
        }
    }

    private void sleep() {
        try {
            Thread.sleep(1000L);
        } catch (InterruptedException e) {
            log.error(e.getMessage());
        }
    }
}
