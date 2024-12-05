package dev.abu.screener_backend.handlers;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
public class WSOrderBookHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = new HashSet<>();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        log.info("New Websocket connection established: {}", session.getId());
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("Websocket connection closed: {}", session.getId());
        sessions.remove(session);
    }

    public synchronized void broadcastOrderBook(String update) {
        sessions.forEach(session -> {
            try {
                session.sendMessage(new TextMessage(update));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }
}