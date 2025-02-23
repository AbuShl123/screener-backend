package dev.abu.screener_backend;

import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.binance.WSDepthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.WebSocketSession;

import java.nio.ByteBuffer;
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

    private static final Map<String, Integer> reSyncCountMap = new ConcurrentHashMap<>();
    private static final Set<WSDepthClient> websockets = new HashSet<>();

    private final TickerService tickerService;
    private int conns = 0;

    public static int reSyncCount(String websocketName) {
        return reSyncCountMap.get(websocketName);
    }

    public static void incrementReSyncCount(String websocketName) {
        reSyncCountMap.put(websocketName, reSyncCountMap.get(websocketName) + 1);
    }

    public static void decrementReSyncCount(String websocketName) {
        reSyncCountMap.put(websocketName, reSyncCountMap.get(websocketName) - 1);
    }

    @Override
    public void run(String... args) {
        List<String> symbols = tickerService.getSpotSymbols();
        log.info("Connecting to {} spot symbols", symbols.size());
        connectByChunks(symbols, true);

        conns = 0;

        symbols = tickerService.getFutSymbols();
        log.info("Connecting to {} fut symbols", symbols.size());
        connectByChunks(symbols, false);
    }

    private void connectByChunks(List<String> symbols, boolean isSpot) {
        int chunkSize = isSpot ? CHUNK_SIZE : CHUNK_SIZE / 2;

        for (int i = 0; i < symbols.size(); i += chunkSize) {
            // get the next CHUNK_SIZE symbols
            List<String> chunk = symbols.subList(i, Math.min(i + chunkSize, symbols.size()));
            log.info("current chunk size: {}", chunk.size());

            // start websocket with the provided chunk of symbols
            var ws = startWebsocket(chunk, isSpot);
            websockets.add(ws);

            // wait until current order book finishes processing initial snapshots
            while (reSyncCount(ws.getName()) < chunk.size()) {
                waitFor(5000L);
            }
            log.info("Finished re-sync for {}", ws.getName());
        }
        log.info("Finished all the chunks for {}", ( isSpot ? "spot" : "futures"));
    }

    private WSDepthClient startWebsocket(List<String> symbols, boolean isSpot) {
        String baseUrl = isSpot ? STREAM_SPOT_URL : STREAM_FUT_URL;
        String stream = isSpot ? "@depth/" : "@depth@500ms/";

        StringBuilder wsUrl = new StringBuilder(baseUrl + "/stream?streams=");
        symbols.forEach(symbol -> wsUrl.append(symbol).append(stream));
        wsUrl.deleteCharAt(wsUrl.length() - 1);

        String name = "[Depth " + (isSpot ? "spot " : "futures ") + conns + ']';
        reSyncCountMap.put(name, 0);
        conns++;

        log.info("connecting to url: {}", wsUrl);
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

    private void sendPingMessage(String name, WebSocketSession session) {
        if (session != null && session.isOpen()) {
            try {
                PingMessage pingMessage = new PingMessage(ByteBuffer.wrap("ping".getBytes()));
                session.sendMessage(pingMessage);
            } catch (Exception e) {
                log.error("{} Failed to send ping message - {}", name, e.getMessage());
            }
        }
    }
}