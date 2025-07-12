package dev.abu.screener_backend.websockets;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.TradeListDTO;
import dev.abu.screener_backend.appuser.AppUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class UserContainer {

    private final ObjectMapper mapper;
    private final AppUser user;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final Map<String, TradeListDTO> orderBook = new ConcurrentHashMap<>();

    public UserContainer(AppUser user, WebSocketSession session, ObjectMapper mapper) {
        this.user = user;
        this.mapper = mapper;
        addSession(session);
    }

    public void addSession(WebSocketSession session) {
        sessions.add(session);
    }

    public void take(String mSymbol, TradeListDTO event) {
        orderBook.put(mSymbol, event);
    }

    public void remove(String mSymbol) {
        orderBook.remove(mSymbol);
    }

    public void broadcastEvents() {
        if (sessions.isEmpty() || orderBook.isEmpty()) {
            return;
        }

        sessions.forEach(session -> {
            if (!session.isOpen()) {
                sessions.remove(session);
                return;
            }
            try {
                byte[] message = mapper.writeValueAsBytes(orderBook.values());
                session.sendMessage(new TextMessage(message));
            } catch (Exception e) {
                log.error("Couldn't send data to user {}: {}", user.getEmail(), e.getMessage());
            }
        });
    }

    @Override
    public int hashCode() {
        return user.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        UserContainer other = (UserContainer) obj;
        return user.equals(other.user);
    }
}
