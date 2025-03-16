package dev.abu.screener_backend;

import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.binance.WSDepthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static dev.abu.screener_backend.utils.EnvParams.*;

@Slf4j
@RequiredArgsConstructor
@Component
public class TasksRunner implements CommandLineRunner {

    private static final Map<String, Set<String>> reSyncCountMap = new ConcurrentHashMap<>();

    private final TickerService tickerService;
    private int conns = 0;

    public static int reSyncCount(String websocketName) {
        return reSyncCountMap.get(websocketName).size();
    }

    public static void incrementReSyncCount(String websocketName, String symbol) {
        reSyncCountMap.get(websocketName).add(symbol);
    }

    public static void decrementReSyncCount(String websocketName, String symbol) {
        reSyncCountMap.get(websocketName).remove(symbol);
    }

    @Override
    public void run(String... args) {
        List<String> symbols = tickerService.getAllSymbols();
        connectByChunks(symbols, true);
        connectByChunks(symbols, false);
    }

    private void connectByChunks(List<String> symbols, boolean isSpot) {
        for (int i = 0; i < symbols.size(); i += CHUNK_SIZE) {
            List<String> chunk = symbols.subList(i, Math.min(i + CHUNK_SIZE, symbols.size()));

            var ws = startWebsocket(chunk, isSpot);

            while (reSyncCount(ws.getName()) < chunk.size()) {
                waitFor(5000L);
            }
            log.info("Finished re-sync for {}", ws.getName());
        }
        log.info("Finished all the chunks for {}", (isSpot ? "spot" : "futures"));
    }

    private WSDepthClient startWebsocket(List<String> symbols, boolean isSpot) {
        String baseUrl = isSpot ? STREAM_SPOT_URL : STREAM_FUT_URL;
        String stream = isSpot ? "@depth/" : "@depth@500ms/";

        StringBuilder wsUrl = new StringBuilder(baseUrl + "/stream?streams=");
        symbols.forEach(symbol -> wsUrl.append(symbol).append(stream));
        wsUrl.deleteCharAt(wsUrl.length() - 1);

        String name = "[Depth " + (isSpot ? "spot " : "futures ") + conns + ']';
        reSyncCountMap.put(name, new HashSet<>());
        conns++;

        WSDepthClient ws = new WSDepthClient(name, wsUrl.toString(), isSpot, symbols.toArray(new String[0]));
        ws.startWebSocket();
        return ws;
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