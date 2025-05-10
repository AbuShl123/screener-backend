package dev.abu.screener_backend.binance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import static dev.abu.screener_backend.binance.OBManager.printReSyncMap;
import static dev.abu.screener_backend.utils.EnvParams.STREAM_FUT_URL;
import static dev.abu.screener_backend.utils.EnvParams.STREAM_SPOT_URL;

@Slf4j
@Service
public class BinanceService {

    private final TickerService tickerService;
    private final MaxOrdersService maxOrdersService;
    private final WSDepthClient spotDepthClient;
    private final WSDepthClient futDepthClient;

    public BinanceService(TickerService tickerService, MaxOrdersService maxOrdersService) {
        this.tickerService = tickerService;
        this.maxOrdersService = maxOrdersService;
        this.spotDepthClient = new WSDepthClient(STREAM_SPOT_URL, true);
        this.futDepthClient = new WSDepthClient(STREAM_FUT_URL, false);
        spotDepthClient.startWebSocket();
        futDepthClient.startWebSocket();
    }

    @Scheduled(fixedDelay = 60_000)
    public void updateTickers() {
        printReSyncMap();
        tickerService.updateTickers();
        maxOrdersService.updateMaxOrders();
        maxOrdersService.updateDepthData();
        spotDepthClient.listenToSymbols(tickerService.getSpotSymbols());
        futDepthClient.listenToSymbols(tickerService.getFutSymbols());
    }

    public boolean isSymbolConnected(String symbol, boolean isSpot) {
        if (isSpot) return spotDepthClient.isSymbolConnected(symbol);
        else return futDepthClient.isSymbolConnected(symbol);
    }

    public static void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Error while waiting: ", e);
        }
    }
}