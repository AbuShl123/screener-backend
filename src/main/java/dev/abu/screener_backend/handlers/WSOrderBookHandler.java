package dev.abu.screener_backend.handlers;

import dev.abu.screener_backend.entity.ScreenerUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashSet;
import java.util.Set;

@Component
@Slf4j
public class WSOrderBookHandler extends TextWebSocketHandler {

    /** Storing all users connected to this websocket */
    private final Set<ScreenerUser> users = new HashSet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        addSession(session);
        log.info("New Websocket connection established: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        closeSession(session);
        log.info("Websocket connection closed: {}", session.getId());
    }

    private void addSession(WebSocketSession session) {
        users.add(new ScreenerUser(session));
    }

    private void closeSession(WebSocketSession session) {
        for (ScreenerUser userSession : users) {
            if (userSession.getId().equals(session.getId())) {
                userSession.closeConnection();
                users.remove(userSession);
            }
        }
    }
}