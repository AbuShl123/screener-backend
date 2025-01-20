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

import java.io.IOException;
import java.util.Arrays;

import static dev.abu.screener_backend.utils.EnvParams.*;

@Setter
@Slf4j
public class WSDepthClient extends WSBinanceClient {

    private WebSocketSession session;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean isSpot;
    private String[] symbols;
    private int counter;

    public WSDepthClient(boolean isSpot, String... symbols) {
        super("Order Book " + (isSpot ? "Spot" : "Futures") + " " + Arrays.toString(symbols));
        this.isSpot = isSpot;
        this.symbols = symbols;
        this.counter = 0;
        setLocalOrderBook();
        setWsUrl(symbols);
        startWebSocket();
    }

    public void closeConnection() {
        try {
            for (String symbol : symbols) {
                var stream = OrderBookStream.getInstance(symbol);
                var orderbook = LocalOrderBook.getInstance(symbol);
                if (stream != null) stream.reset();
                if (orderbook != null) orderbook.reset();
            }
            session.close();
        } catch (Exception e) {
            log.error("Failed to close websocket connection {}", e.getMessage());
        }
    }

    private void setLocalOrderBook() {
        for (String symbol : symbols) {
            String s = isSpot ? symbol : symbol + FUT_SIGN;
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
            WSDepthClient.this.session = session;
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
            log.info("Disconnected from {} : reason = {}", websocketName, status.getReason());
            if (status.getReason() != null) {
                reconnect();
            }
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