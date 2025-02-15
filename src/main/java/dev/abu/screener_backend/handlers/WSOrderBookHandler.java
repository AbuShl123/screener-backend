package dev.abu.screener_backend.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
@Slf4j
@RequiredArgsConstructor
public class WSOrderBookHandler extends TextWebSocketHandler {

    private final SessionPool sessionPool;

    @Override
    public synchronized void afterConnectionEstablished(@NonNull WebSocketSession session) throws IOException {
        sessionPool.addSession(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("Order book client session closed: {} - {}", session.getId(), status);
    }

    @Scheduled(fixedRate = 3000)
    public void sendUpdates() {
        sessionPool.clearClosedSessions();
        sessionPool.sendData();
    }
}