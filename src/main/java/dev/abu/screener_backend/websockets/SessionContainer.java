package dev.abu.screener_backend.websockets;

import dev.abu.screener_backend.analysis.TradeListDTO;
import lombok.Getter;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashSet;
import java.util.Set;

@Getter
public class SessionContainer {
    private final Set<WebSocketSession> sessions = new HashSet<>();
    private final Set<TradeListDTO> events = new HashSet<>();

    public void addEvent(TradeListDTO event) {
        events.add(event);
    }

    public void clearEvents() {
        events.clear();
    }

    public void removeSession(WebSocketSession session) {
        sessions.remove(session);
    }

    public void addSession(WebSocketSession session) {
        sessions.add(session);
    }
}
