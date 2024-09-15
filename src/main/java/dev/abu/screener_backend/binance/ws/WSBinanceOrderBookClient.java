package dev.abu.screener_backend.binance.ws;


import dev.abu.screener_backend.binance.Ticker;
import dev.abu.screener_backend.handlers.WSOrderBookHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
public class WSBinanceOrderBookClient extends WSBinanceClient {

    public WSBinanceOrderBookClient(Ticker symbol) {
        super("Order Book", "/{symbol}@depth", symbol, true);
    }

    @Override
    protected TextWebSocketHandler getWebSocketHandler() {
        return new BinanceWebsocketHandler();
    }

    private class BinanceWebsocketHandler extends TextWebSocketHandler {

        @Override
        public void afterConnectionEstablished(WebSocketSession session) {
            log.info("Connected to {}: {}", websocketName, session.getId());
            setBinanceSession(session);
        }

        @Override
        protected void handleTextMessage(WebSocketSession session, TextMessage message) {
            String payload = message.getPayload();
            WSOrderBookHandler.broadCastOrderBookData(payload, symbol);
        }

        @Override
        public void handleTransportError(WebSocketSession session, Throwable exception) {
            log.error("Transport error in {}: {}", websocketName, exception.getMessage());
            reconnect();
        }

        @Override
        public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
            log.info("Disconnected from {}: {}", websocketName, status.getReason());
        }
    }
}