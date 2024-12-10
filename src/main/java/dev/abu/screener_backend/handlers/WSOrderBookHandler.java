package dev.abu.screener_backend.handlers;

import dev.abu.screener_backend.binance.jpa.TickerService;
import dev.abu.screener_backend.rabbitmq.RabbitMQService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class WSOrderBookHandler extends TextWebSocketHandler {

    private final Map<String, ClientSession> sessions = new HashMap<>();
    private final RabbitMQService rabbitMQService;
    private final TickerService tickerService;

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        log.info("New Websocket connection established: {}", session.getId());
        ClientSession clientSession = new ClientSession(session, rabbitMQService, tickerService);
        if (clientSession.isOpen()) {
            sessions.put(session.getId(), clientSession);
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("Websocket connection closed: {}", session.getId());
        var removedSession = sessions.remove(session.getId());
        if (removedSession != null) {
            removedSession.closeSession();
        }
    }
}