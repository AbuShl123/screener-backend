package dev.abu.screener_backend.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashSet;

@Component
@Slf4j
@RequiredArgsConstructor
public class WSOrderBookHandler extends TextWebSocketHandler {

    private final HashSet<ClientSession> sessions = new HashSet<>();

    @Override
    public synchronized void afterConnectionEstablished(@NonNull WebSocketSession session) {
        createSession(session);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("Websocket connection closed: {}", session.getId());
    }

    private void createSession(WebSocketSession session) {
        var clientSession = ClientSession.startClientSession(session);
        sessions.add(clientSession);
    }

    @Scheduled(fixedRate = 1000)
    public void sendUpdates() {
        sessions.forEach(ClientSession::sendData);
        sessions.removeIf(ClientSession::isNotOpen);
    }
}