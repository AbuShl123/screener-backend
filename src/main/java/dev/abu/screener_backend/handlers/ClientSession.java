package dev.abu.screener_backend.handlers;

import dev.abu.screener_backend.analysis.OrderBookStream;
import dev.abu.screener_backend.binance.WSDepthClient;
import dev.abu.screener_backend.entity.Trade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

import static dev.abu.screener_backend.utils.RequestUtilities.getQueryParams;

@Slf4j
public class ClientSession {

    private static final String INVALID_SYMBOL_MESSAGE = """
            {
            "error": "The 'symbol' parameter is invalid or missing."
            }
            """;

    private final WebSocketSession session;
    private final HashSet<String> symbols = new HashSet<>();

    public static ClientSession startClientSession(WebSocketSession session) {
        return new ClientSession(session);
    }

    private ClientSession(WebSocketSession session) {
        this.session = session;
        String[] queues = getQueues();
        if (queues == null) {
            sendMessage(INVALID_SYMBOL_MESSAGE);
            closeSession();
            return;
        }
        log.info("Client session created for symbols {}", symbols);
    }

    public boolean isNotOpen() {
        return !session.isOpen();
    }

    public void sendData() {
        for (String symbol : symbols) {
            OrderBookStream stream = OrderBookStream.getInstance(symbol);
            broadCastData(symbol, stream.getBids(), stream.getAsks());
        }
    }

    private void broadCastData(String symbol, List<Trade> bids, List<Trade> asks) {
        try {
            if (!session.isOpen()) return;
            String message = String.format("""
                    {
                    "symbol": "%s",
                    "b": %s,
                    "a": %s
                    }
                    """, symbol, bids, asks);
            sendMessage(message);
        } catch (Exception e) {
            log.error("{} - Couldn't broadcast data: {}", symbol, e.getMessage());
            closeSession();
        }
    }

    public void closeSession() {
        if (session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.error("Couldn't close session", e);
            }
        }
        log.info("Closed session for: {}", session.getId());
    }

    private String[] getQueues() {
        var queryParamsMap = getQueryParams(session);
        String symbolsStr = queryParamsMap.get("symbols");
        if (symbolsStr == null || symbolsStr.isEmpty()) return null;
        String[] symbols = symbolsStr.split("/");
        HashSet<String> queues = new HashSet<>();
        for (String symbol : symbols) {
            this.symbols.add(symbol.trim().toLowerCase());
            String queue = WSDepthClient.getQueue(symbol);
            if (queue == null) return null;
            queues.add(queue);
        }
        return queues.toArray(new String[0]);
    }

    private void sendMessage(String message) {
        try {
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.error("Couldn't send a message - {}.", message, e);
            closeSession();
        }
    }
}
