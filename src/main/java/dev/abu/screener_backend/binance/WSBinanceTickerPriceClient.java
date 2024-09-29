package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Getter
@Slf4j
public class WSBinanceTickerPriceClient extends BinanceWebSocket {

    private double price = 0.0;

    public WSBinanceTickerPriceClient(Ticker symbol) {
        super("Ticker Price", "/{symbol}@ticker", symbol, false);
    }

    @Override
    protected TextWebSocketHandler getWebSocketHandler() {
        return new TickerPriceHandler();
    }

    private void setPrice(String payload) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode responsePayload = mapper.readTree(payload);
            this.price = responsePayload.get("c").asDouble();
        } catch (Exception e) {
            log.error("Failed to read ticker price in {}", websocketName, e);
        }
    }

    private class TickerPriceHandler extends TextWebSocketHandler {
        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            log.info("Connected to {}: {}", websocketName, session.getId());
            setBinanceSession(session);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            setPrice(message.getPayload());
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.error("Transport error: {}", exception.getMessage());
            reconnect();
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            log.info("Disconnected from {}: {}", websocketName, status.getReason());
        }
    }
}