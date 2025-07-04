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
    private final WebSocketSession session;
    private final Map<String, TradeListDTO> orderBook = new ConcurrentHashMap<>();

    public UserContainer(AppUser user, WebSocketSession session, ObjectMapper mapper) {
        this.user = user;
        this.session = session;
        this.mapper = mapper;
    }

    public void take(String mSymbol, TradeListDTO event) {
        orderBook.put(mSymbol, event);
    }

    public void remove(String mSymbol) {
        orderBook.remove(mSymbol);
    }

    public void broadcastEvents() {
        if (!session.isOpen() || orderBook.isEmpty()) {
            return;
        }

        try {
            byte[] message = mapper.writeValueAsBytes(orderBook.values());
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.error("Couldn't send data to user {}: {}", user.getEmail(), e.getMessage());
        }
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
