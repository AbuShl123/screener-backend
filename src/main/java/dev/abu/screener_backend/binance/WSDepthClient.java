package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.LocalOrderBook;
import dev.abu.screener_backend.analysis.OrderBookStream;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static dev.abu.screener_backend.utils.EnvParams.*;

@Setter
@Slf4j
public class WSDepthClient extends WSBinanceClient {

    private final static Map<String, String> depthMap = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean isSpot;
    private final String queue;
    private final String[] symbols;
    private int counter;

    public WSDepthClient(boolean isSpot, String depth, String... symbols) {
        super("Order Book " + (isSpot ? "Spot" : "Futures") + " [" + depth + "]");
        this.isSpot = isSpot;
        this.queue = depth;
        this.symbols = symbols;
        this.counter = 0;
        setDepthMap();
        setWsUrl(symbols);
        startWebSocket();
    }

    public static String getQueue(String symbol) {
        return depthMap.get(symbol);
    }

    private void setDepthMap() {
        for (String symbol : symbols) {
            String s = isSpot ? symbol : symbol + FUT_SIGN;
            depthMap.put(s, queue);
            OrderBookStream.createInstance(s);
            LocalOrderBook.createInstance(s, isSpot);
        }
    }

    protected void setWsUrl(String... symbols) {
        String stream = isSpot ? "@depth/" : "@depth@500ms/";
        StringBuilder path = new StringBuilder("stream?streams=");
        for (String symbol : symbols) {
            path.append(symbol.toLowerCase()).append(stream);
        }
        path.deleteCharAt(path.length() - 1);
        this.wsUrl = (isSpot ? STREAM_SPOT_URL : STREAM_FUT_URL) + "/" + path;
    }

    @Override
    protected TextWebSocketHandler getWebSocketHandler() {
        return new OrderBookHandler();
    }

    private class OrderBookHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(@NonNull WebSocketSession session) {
            log.info("Connected to {}: {} - {}", websocketName, session.getId(), wsUrl);
        }

        @Override
        protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) throws JsonProcessingException {
            String payload = message.getPayload();
            String symbol = extractSymbol(payload);
            if (symbol == null) return;
            String stream = isSpot ? symbol : symbol + FUT_SIGN;
            LocalOrderBook ob = LocalOrderBook.getInstance(stream);
            if (counter == -1 || ob.isReSyncCompleted()) {
                ob.process(payload);
                return;
            }

            if (!symbol.equals(symbols[counter])) return;
            ob.process(payload);
            if (ob.isReSyncCompleted()) counter++;
            if (counter >= symbols.length) counter = -1;
        }

        @Override
        public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
            log.error("Transport error: {}", exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
            log.info("Disconnected from {} (symbols={}) : reason = {}", websocketName, symbols, status.getReason());
            reconnect();
            reSyncLocalOrderBooks();
        }
    }

    private void reSyncLocalOrderBooks() {
        for (String symbol : symbols) {
            LocalOrderBook.getInstance(symbol).reSyncOrderBook();
        }
    }

    public String extractSymbol(String json) {
        try {
            JsonNode rootNode = mapper.readTree(json);
            String streamValue = rootNode.get("stream").asText();
            return streamValue.split("@")[0];
        } catch (Exception e) {
            return null;
        }
    }
}