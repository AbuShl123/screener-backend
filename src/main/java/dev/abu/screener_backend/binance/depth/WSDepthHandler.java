package dev.abu.screener_backend.binance.depth;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.OBMessageHandler;
import dev.abu.screener_backend.binance.OBService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.BinaryWebSocketHandler;

@Slf4j
public class WSDepthHandler extends BinaryWebSocketHandler {

    private final String name;
    private final WSDepthClient wsDepthClient;
    private final OBMessageHandler messageHandler;
    private final ObjectMapper mapper;

    public WSDepthHandler(
            String name,
            boolean isSpot,
            WSDepthClient wsDepthClient,
            ObjectMapper mapper,
            OBService obService
    ) {
        this.name = name;
        this.wsDepthClient = wsDepthClient;
        this.messageHandler = new OBMessageHandler(isSpot, obService);
        this.mapper = mapper;
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        try {
            DepthUpdate update = mapper.readValue(message.asBytes(), DepthUpdate.class);
            messageHandler.take(update);
        } catch (Exception e) {
            log.error("Failed to deserialize depth event: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        log.info("{} websocket connection established.", name);
    }

    @Override
    public void handleTransportError(@NonNull WebSocketSession session, @NonNull Throwable exception) {
        log.error("Transport error: {}", exception.getMessage());
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        log.info("{} Disconnected from websocket with code {} and reason \"{}\".", name, status.getCode(), status.getReason());
        if (status.getReason() != null || status.getCode() == 1006) {
            wsDepthClient.reconnect();
        }
    }
}
