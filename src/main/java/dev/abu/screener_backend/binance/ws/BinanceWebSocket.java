package dev.abu.screener_backend.binance.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.OBService;
import dev.abu.screener_backend.binance.entities.KlineInterval;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static dev.abu.screener_backend.utils.EnvParams.STREAM_FUT_URL;
import static dev.abu.screener_backend.utils.EnvParams.STREAM_SPOT_URL;

@Component
@RequiredArgsConstructor
@Slf4j
public class BinanceWebSocket {

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final int futWsCount = 1;
    private static final URI spotEndpoint = URI.create(STREAM_SPOT_URL);
    private static final URI futEndpoint = URI.create(STREAM_FUT_URL);

    private final ObjectMapper objectMapper;
    private final WebSocketClient client;
    private final OBService obService;
    private final WSMessageFilter filter;

    private WebSocketManager spotWs;
    private List<WebSocketManager> futWebSockets;

    @PostConstruct
    public void start() {
        futWebSockets = new ArrayList<>();
        WSMessageHandler spotMsgHandler = new WSMessageHandler(filter, true);
        WSMessageHandler futMsgHandler = new WSMessageHandler(filter, false);

        spotWs = new WebSocketManager(client, spotEndpoint, "SPOT", spotMsgHandler, objectMapper, scheduler);
        spotWs.start();
        for (int i = 1; i <= futWsCount; i++) {
            var futWs = new WebSocketManager(client, futEndpoint, "FUT" + i, futMsgHandler, objectMapper, scheduler);
            futWs.start();
            futWebSockets.add(futWs);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdownNow();
    }

    public void subscribeToDepth(Collection<String> symbols, boolean isSpot) {
        obService.prepareOrderBooks(symbols, isSpot);
        List<String> streams = new ArrayList<>(symbols.size());
        String prefix = isSpot ? "@depth" : "@depth@500ms";
        symbols.forEach(symbol -> streams.add(symbol + prefix));
        subscribe(streams, isSpot);
    }

    public void subscribeToKline(Collection<String> symbols, KlineInterval interval, boolean isSpot) {
        obService.prepareOrderBooks(symbols, isSpot);
        List<String> streams = new ArrayList<>(symbols.size());
        String prefix = "@kline_" + interval.getCode();
        symbols.forEach(symbol -> streams.add(symbol + prefix));
        subscribe(streams, isSpot);
    }

    private void subscribe(List<String> streams, boolean isSpot) {
        if (isSpot) {
            spotWs.subscribe(streams);
            return;
        }

        int chunkSize = (int) Math.ceil((double) streams.size() / futWsCount);
        int c = 0;
        for (int i = 0; i < streams.size(); i+=chunkSize, c++) {
            List<String> subStreams = streams.subList(i, Math.min(streams.size(), i + chunkSize));
            futWebSockets.get(c).subscribe(subStreams);
        }
    }
}