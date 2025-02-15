package dev.abu.screener_backend;

import dev.abu.screener_backend.binance.OrderBook;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.binance.WSDepthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

import static dev.abu.screener_backend.utils.EnvParams.CHUNK_SIZE;
import static dev.abu.screener_backend.utils.EnvParams.STREAM_SPOT_URL;

@Slf4j
@RequiredArgsConstructor
@Component
public class TasksRunner implements CommandLineRunner {

    private static int conns = 0;

    private final TickerService tickerService;

    @Override
    public void run(String... args) {
        establishConnections();
    }

    private void establishOneConnection() {
        List<String> symbols = tickerService.getSpotSymbols().subList(0, 10);
        startWebsocket(symbols, true);
    }

    private void establishConnections() {
        List<String> symbols = tickerService.getSpotSymbols();

        for (int i = 0; i < symbols.size(); i += CHUNK_SIZE) {
            // get the next CHUNK_SIZE symbols
            List<String> chunk = symbols.subList(i, Math.min(i + CHUNK_SIZE, symbols.size()));

            // start websocket with the provided chunk of symbols
            var ws = startWebsocket(chunk, true);

            // wait until current order book finishes sending initial snapshots
            while (OrderBook.reSyncCount(ws.getName()) < CHUNK_SIZE) {
                waitFor(5000L);
            }
        }
    }

    private WSDepthClient startWebsocket(List<String> symbols, boolean isSpot) {
        StringBuilder wsUrl = new StringBuilder(STREAM_SPOT_URL + "/stream?streams=");
        symbols.forEach(symbol -> wsUrl.append(symbol).append("@depth/"));
        wsUrl.deleteCharAt(wsUrl.length() - 1);
        String name = "[Depth " + (isSpot ? "spot " : "futures ") + conns + ']';
        conns++;
        WSDepthClient ws = new WSDepthClient(name, wsUrl.toString(), true, symbols.toArray(new String[0]));
        ws.startWebSocket();
        return ws;
    }

    private void waitFor(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
