package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.net.URI;

@Slf4j
public class DepthWebSocket extends WebSocketClient {

    private final ObjectMapper mapper = new ObjectMapper();

    public DepthWebSocket(URI serverUri, String... symbols) {
        super(serverUri);
    }

    @Override
    public void onOpen(ServerHandshake serverHandshake) {
        log.info("Connected to Order Book WebSocket.");
    }

    @Override
    public void onMessage(String s) {
        try {
            JsonNode root = mapper.readTree(s);

            JsonNode node = root.get("s");
            if (node == null) return;
            String symbol = node.asText().toLowerCase();

            JsonNode bidsArray = root.get("b");
            JsonNode asksArray = root.get("a");
            if (bidsArray == null || asksArray == null) return;


        } catch (Exception e) {
            log.error("failed to read json data: {} - {}", e.getMessage(), s);
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        log.info("Order Book WebSocket connection closed {} -{}", code, reason);
    }

    @Override
    public void onError(Exception e) {
        log.error("Order Book WebSocket error: {}", e.getMessage());
    }

    private void sendInitialSnapshots(String[] symbols) {

    }
}
