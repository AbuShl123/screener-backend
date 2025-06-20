package dev.abu.screener_backend.websockets;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
@Slf4j
@RequiredArgsConstructor
public class WSDepthHandler extends TextWebSocketHandler {

    private final SessionManager sessionManager;

    @Override
    public synchronized void afterConnectionEstablished(@NonNull WebSocketSession session) {
        sessionManager.addSession(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {

    }
}