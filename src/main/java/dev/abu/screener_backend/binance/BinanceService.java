package dev.abu.screener_backend.binance;

import dev.abu.screener_backend.analysis.OBMessageHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static dev.abu.screener_backend.binance.OBManager.*;
import static dev.abu.screener_backend.utils.EnvParams.*;

@RequiredArgsConstructor
@Slf4j
@Service
public class BinanceService {

    private static final Set<String> connectedSymbols = new HashSet<>();

    private final TickerService tickerService;
    private final MaxOrdersService maxOrdersService;

    private WSDepthClient spotDepthClient;
    private WSDepthClient futDepthClient;
    private final OBMessageHandler spotMessageHandler = new OBMessageHandler(true);
    private final OBMessageHandler futMessageHandler = new OBMessageHandler(false);
    private volatile int statusOfInitialRun = 0;
    private int wsCount;

    public static boolean isSymbolConnected(String marketSymbol) {
        return connectedSymbols.contains(marketSymbol);
    }

    @Scheduled(fixedDelay = 60_000)
    public void updateTickers() {
        tickerService.updateTickers();
        if (statusOfInitialRun == 0) {
            startConnecting();
        }
        if (statusOfInitialRun == 2) {
            checkNewTickers();
        }
        maxOrdersService.updateMaxOrders();
        printReSyncMap();
    }

    private void startConnecting() {
        if (statusOfInitialRun != 0) return;
        statusOfInitialRun = 1;
        var exec = Executors.newSingleThreadExecutor();
        exec.submit(() -> {
            try {
                var symbols = tickerService.getAllSymbols();
                var symbolsList = new ArrayList<>(symbols);
                connectByChunks(symbolsList, true);
                connectByChunks(symbolsList, false);
            } finally {
                statusOfInitialRun = 2;
            }
        });
    }

    private void checkNewTickers() {
        if (statusOfInitialRun != 2) return;

        Set<String> updatedSymbols = tickerService.getAllSymbols();
        Set<String> newSymbols = getNewSymbols(updatedSymbols);
        Set<String> deletedSymbols = getDeletedSymbols(updatedSymbols);

        if (!deletedSymbols.isEmpty()) {
            log.info("Dropping deleted tickers: {}", deletedSymbols);
            connectedSymbols.removeAll(deletedSymbols);
            dropOrderBooks(deletedSymbols);
        }

        if (newSymbols.isEmpty()) return;

        if (spotDepthClient == null || futDepthClient == null) {
            log.info("Discovered new tickers for the first time: {} - connecting to them", newSymbols);
            spotDepthClient = createWSDepthClient(newSymbols, true);
            futDepthClient = createWSDepthClient(newSymbols, false);
        } else {
            prepareOrderBooks(newSymbols, true, spotDepthClient.websocketName);
            prepareOrderBooks(newSymbols, false, futDepthClient.websocketName);

            var spotSymbols = spotDepthClient.getSymbols(); // returns actual reference
            spotSymbols.addAll(newSymbols);
            log.info("new spot symbols: {}", spotSymbols);
            spotDepthClient.reconnect(buildDepthUrl(spotSymbols, true));
            var futSymbols = futDepthClient.getSymbols();
            futSymbols.addAll(newSymbols);
            log.info("new future symbols: {}", futSymbols);
            futDepthClient.reconnect(buildDepthUrl(futSymbols, false));
        }

        connectedSymbols.addAll(newSymbols);
    }

    private void connectByChunks(List<String> symbols, boolean isSpot) {
        List<WSDepthClient> websockets = new ArrayList<>();

        int chunkSize = isSpot ? CHUNK_SIZE : CHUNK_SIZE - 50;
        if (chunkSize == 0) throw new IllegalStateException("Chunk size is 0");
        for (int i = 0; i <= symbols.size(); i += chunkSize) {
            List<String> chunk = symbols.subList(i, Math.min(i + chunkSize, symbols.size()));
            var ws = createWSDepthClient(chunk, isSpot);
            while (!ws.isConnected()) waitFor(1000L);
            websockets.add(ws);
        }

        for (WSDepthClient websocket : websockets) {
            String name = websocket.getName();
            websocket.turnOn();
            while (reSyncCount(name) < websocket.getSymbols().size()) {
                printReSyncMap();
                waitFor(5000L);
            }
        }
    }

    private WSDepthClient createWSDepthClient(Collection<String> symbols, boolean isSpot) {
        String wsUrl = buildDepthUrl(symbols, isSpot);
        String name = "[" + (isSpot ? "spot" : "futures") + wsCount++ + ']';
        prepareReSyncMap(name);
        prepareOrderBooks(symbols, isSpot, name);

        var messageHandler = isSpot ? spotMessageHandler : futMessageHandler;
        WSDepthClient ws = new WSDepthClient(name, wsUrl, messageHandler, symbols);
        ws.startWebSocket();

        var marketSymbols = isSpot ? symbols : symbols.stream().map(s -> s + FUT_SIGN).collect(Collectors.toSet());
        connectedSymbols.addAll(marketSymbols);
        return ws;
    }

    private String buildDepthUrl(Collection<String> symbols, boolean isSpot) {
        String baseUrl = isSpot ? STREAM_SPOT_URL : STREAM_FUT_URL;
        String streamSuffix = isSpot ? "@depth" : "@depth@500ms";

        String streams = symbols.stream()
                .map(symbol -> symbol + streamSuffix)
                .collect(Collectors.joining("/"));

        return baseUrl + "/stream?streams=" + streams;
    }

    private Set<String> getNewSymbols(Set<String> allSymbols) {
        return allSymbols.stream()
                .filter(symbol -> !connectedSymbols.contains(symbol))
                .collect(Collectors.toSet());
    }

    private Set<String> getDeletedSymbols(Set<String> allSymbols) {
        return connectedSymbols.stream()
                .filter(symbol -> !allSymbols.contains(symbol.replace(FUT_SIGN, "")))
                .collect(Collectors.toSet());
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
