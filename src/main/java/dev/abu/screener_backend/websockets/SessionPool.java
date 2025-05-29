package dev.abu.screener_backend.websockets;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import dev.abu.screener_backend.analysis.TradeListDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
@Slf4j
@RequiredArgsConstructor
public class SessionPool {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, TradeListDTO> eventsMap = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    public void addSession(WebSocketSession session) {
        sessions.add(session);
    }

    public void removeClosedSessions() {
        sessions.removeIf(session -> !session.isOpen());
    }

    public synchronized void addData(TradeListDTO tradeList) {
        eventsMap.put(tradeList.s(), tradeList);
    }

    public synchronized void removeData(String symbol) {
        eventsMap.remove(symbol);
    }

    public synchronized void sendData() {
        var events = eventsMap.values();
        if (sessions.isEmpty() || events.isEmpty()) return;
        ArrayNode array = mapper.createArrayNode();
        for (TradeListDTO tradeList : events) {
            JsonNode node = mapper.valueToTree(tradeList);
            array.add(node);
        }
        serializeAndSend(array);
    }

    private void serializeAndSend(ArrayNode data) {
        try {
            String json = mapper.writeValueAsString(data);
            broadCastMessage(json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize trade list DTO: {}", e.getMessage());
        }
    }

    private void broadCastMessage(String message) {
        sessions.forEach(session -> {
           try {
               if (session.isOpen()) {
                   session.sendMessage(new TextMessage(message));
               }
           } catch (IOException e) {
               log.error("Couldn't send message: {}", e.getMessage());
           }
        });
    }
}