package dev.abu.screener_backend.handlers;

import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.rabbitmq.RabbitMQService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@Slf4j
@RequiredArgsConstructor
public class WSOrderBookHandler extends TextWebSocketHandler {

    private final RabbitMQService rabbitMQService;
    private final TickerService tickerService;

    @Override
    public synchronized void afterConnectionEstablished(@NonNull WebSocketSession session) {
        createSession(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("Websocket connection closed: {}", session.getId());
    }

    private void createSession(WebSocketSession session) {
        ClientSession.startClientSession(session, rabbitMQService, tickerService);
    }

}