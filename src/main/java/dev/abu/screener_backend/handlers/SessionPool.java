package dev.abu.screener_backend.handlers;

import dev.abu.screener_backend.analysis.OrderBookStream;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.entity.Trade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;

import static dev.abu.screener_backend.TasksRunner.waitFor;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;
import static dev.abu.screener_backend.utils.RequestUtilities.getQueryParams;

@Component
@Slf4j
public class SessionPool {

    private static final String INVALID_SYMBOL_MESSAGE = """
            {
            "error": "The 'symbol' parameter is invalid or missing."
            }
            """;

    /** Full list of sessions */
    private final Set<WebSocketSession> sessions;

    /** Map of session->lists */
    private final Map<WebSocketSession, Set<String>> symbolsMap;

    /** List of spot and fut symbols connected */
    private final Set<String> symbolsFlat;

    /** List of all symbols */
    private final List<String> allSymbols;

    public SessionPool(TickerService tickerService) {
        this.sessions = new HashSet<>();
        this.symbolsMap = new HashMap<>();
        this.symbolsFlat = new HashSet<>();
        this.allSymbols = tickerService.getAllSymbols();
    }

    public void addSession(WebSocketSession session) throws IOException {
        if (!checkInputData(session)) {
            sendMessage(session, INVALID_SYMBOL_MESSAGE);
            session.close();
            return;
        }
        sessions.add(session);
        log.info("Order book client session created for symbols {}", symbolsMap.get(session));
    }

    public void clearClosedSessions() {
        sessions.removeIf(session -> !session.isOpen());
        symbolsMap.entrySet().removeIf(entry -> !entry.getKey().isOpen());
        var flatSymbols = symbolsMap.values().stream().flatMap(Set::stream).toList();
        symbolsFlat.removeIf(symbol -> !flatSymbols.contains(symbol));
    }

    public void sendData() {
        Set<String> symbols = new HashSet<>(this.symbolsFlat); // to avoid concurrent modification exception
        Set<WebSocketSession> sessions = new HashSet<>(this.sessions);

        for (String symbol : symbols) {
            OrderBookStream stream = OrderBookStream.getInstance(symbol);
            if (stream == null) return;
            var bids = stream.getBids();
            var asks = stream.getAsks();

            sessions.forEach(session -> {
                if (symbolsMap.get(session).contains(symbol)) {
                    broadCastData(session, symbol, bids, asks);
                }
            });
            waitFor(100);
        }
    }

    private void broadCastData(WebSocketSession session, String symbol, List<Trade> bids, List<Trade> asks) {
        try {
            String message = String.format("""
                    {
                    "symbol": "%s",
                    "b": %s,
                    "a": %s
                    }
                    """, symbol, bids, asks);
            sendMessage(session, message);
        } catch (Exception e) {
            log.error("{} - Couldn't broadcast data: {}", symbol, e.getMessage());
        }
    }

    private void sendMessage(WebSocketSession session, String message) {
        try {
            if (!session.isOpen()) return;
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.error("Couldn't send a message - {} - {}", message, e.getMessage());
        }
    }

    private boolean checkInputData(WebSocketSession session) {
        var queryParamsMap = getQueryParams(session);
        String symbolsStr = queryParamsMap.get("symbols");
        if (symbolsStr == null || symbolsStr.isEmpty()) return false;
        String[] arr = symbolsStr.split("/");
        Set<String> set = new HashSet<>();

        for (String s : arr) {
            String symbol = s.trim().toLowerCase();
            if (!allSymbols.contains(symbol.replace(FUT_SIGN, ""))) return false;
            set.add(symbol);
        }

        this.symbolsMap.put(session, set);
        this.symbolsFlat.addAll(set);
        return true;
    }
}