package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.binance.depth.WSFutDepthClient;
import dev.abu.screener_backend.binance.depth.WSSpotDepthClient;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Set;

import static dev.abu.screener_backend.binance.OBService.printReSyncMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class BinanceService {

    private final TickerService tickerService;
    private final OBService OBService;
    private final WSSpotDepthClient spotDepthClient;
    private final WSFutDepthClient futDepthClient;

    @PostConstruct
    public void setup() {
        spotDepthClient.startWebSocket();
        futDepthClient.startWebSocket();
        syncEveryMinute();
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void syncEveryMinute() {
        printReSyncMap();
        tickerService.syncTickerPrices();
        OBService.updateDistancesAndLevels();
    }

    @Scheduled(fixedDelay = 15 * 60_000)
    public void tickersUpdate() {
        tickerService.updateTickers();
        spotDepthClient.listenToSymbols(Set.of("bnbusdt"));
        futDepthClient.listenToSymbols(Set.of("bnbusdt"));
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 180_000)
    public void sendPongMessage() {
        spotDepthClient.sendPongMessage();
        futDepthClient.sendPongMessage();
    }
}