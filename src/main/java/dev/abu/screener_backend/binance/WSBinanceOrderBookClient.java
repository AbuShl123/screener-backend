package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Setter
@Slf4j
public class WSBinanceOrderBookClient extends WSBinanceClient {

    private RabbitTemplate rabbitTemplate;
    private final String queue;
    private final String[] symbols;

    /**
     * Native Binance WebSocket for receiving Order Book data.
     *
     * @param symbols for which data should be received.
     */
    public WSBinanceOrderBookClient(String queue, String... symbols) {
        super("Order Book [" + queue + "]");
        this.queue = queue;
        this.symbols = symbols;
        setWsUrl(symbols);
        startWebSocket(true);
    }

    private void setWsUrl(String... symbols) {
        StringBuilder sb = new StringBuilder("stream?streams=");
        for (String symbol : symbols) {
            sb.append(symbol.toLowerCase()).append("@depth/");
        }
        sb.deleteCharAt(sb.length() - 1);
        setWsUrl(sb.toString());
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
        protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
            String payload = message.getPayload();
            String symbol = extractSymbol(payload);
            if (symbol == null) {
                log.error("Couldn't get symbol from payload.");
                return;
            }
            rabbitTemplate.convertAndSend(queue, new String[]{symbol, payload});
        }

        @Override
        public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
            log.error("Transport error: {}", exception.getMessage());
        }

        @Override
        public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
            log.info("Disconnected from {} (symbols={}) : reason = {}", websocketName, symbols, status.getReason());
            reconnect();
            sendHistoricalData(symbols);
        }
    }

    private void sendHistoricalData(String[] symbols) {
        int counter = 15;
        for (int i = 0; i < symbols.length; i++) {
            String symbol = symbols[i];
            if (counter == 15) {
                log.info("Currently on: {} ({})", symbol, i);
                counter = 0;
            }
            String payload = BinanceOrderBookClient.getOrderBook(symbol);
            rabbitTemplate.convertAndSend("depth_historical", new String[] {symbol, payload});
            counter++;
        }
        log.info("Success: all historical data for {} are sent.", queue);
    }

    public String extractSymbol(String json) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readTree(json);
            String streamValue = rootNode.get("stream").asText();
            return streamValue.split("@")[0];
        } catch (Exception e) {
            return null;
        }
    }
}
