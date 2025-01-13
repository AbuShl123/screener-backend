package dev.abu.screener_backend.handlers;

import dev.abu.screener_backend.analysis.OrderBookStream;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.binance.WSDepthClient;
import dev.abu.screener_backend.entity.Trade;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;

import static dev.abu.screener_backend.binance.TickerService.popularTickers;
import static dev.abu.screener_backend.utils.EnvParams.CHUNK_SIZE;
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

    private final Set<WebSocketSession> sessions;
    private final Map<WebSocketSession, Set<String>> symbolsMap;
    private final Set<String> symbols;
    private final List<String> spotSymbols;
    private final List<String> futSymbols;
    private final Map<String, WSDepthClient> wsClients;

    public SessionPool(TickerService tickerService) {
        this.sessions = new HashSet<>();
        this.symbolsMap = new HashMap<>();
        this.symbols = new HashSet<>();
        this.spotSymbols = tickerService.getSpotSymbols();
        this.futSymbols = tickerService.getFutSymbols();
        this.wsClients = new HashMap<>();
    }

    public void addSession(WebSocketSession session) throws IOException {
        if (!setSymbols(session)) {
            sendMessage(session, INVALID_SYMBOL_MESSAGE);
            session.close();
            return;
        }
        sessions.add(session);
        log.info("Client session created for symbols {}", symbolsMap.get(session));
    }

    public void sendData() {
        Set<String> symbols = new HashSet<>(this.symbols); // to avoid concurrent modification exception
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
        }
    }

    public void clearClosedSessions() {
        sessions.removeIf(session -> !session.isOpen());
        symbolsMap.entrySet().removeIf(entry -> !entry.getKey().isOpen());
        var flatSymbols = symbolsMap.values().stream().flatMap(Set::stream).toList();
        symbols.removeIf(symbol -> !flatSymbols.contains(symbol));
    }

    public void closeUnusedWSConnections() {
        List<String> popularSymbols = Arrays.asList(popularTickers);
        wsClients.entrySet().removeIf(entry -> {
            if (!symbols.contains(entry.getKey()) && !popularSymbols.contains(entry.getKey())) {
                entry.getValue().closeConnection();
                return true;
            }
            return false;
        });
    }

    private void broadCastData(WebSocketSession session, String symbol, List<Trade> bids, List<Trade> asks) {
        try {
            if (!session.isOpen()) return;
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
            session.sendMessage(new TextMessage(message));
        } catch (IOException e) {
            log.error("Couldn't send a message - {}.", message, e);
        }
    }

    private boolean setSymbols(WebSocketSession session) {
        var queryParamsMap = getQueryParams(session);
        String symbolsStr = queryParamsMap.get("symbols");
        if (symbolsStr == null || symbolsStr.isEmpty()) return false;
        String[] arr = symbolsStr.split("/");
        Set<String> set = new HashSet<>();

        for (String s : arr) {
            String symbol = s.trim().toLowerCase();
            if (!isValidSymbol(symbol)) return false;
            checkWSConnection(symbol);
            set.add(symbol);
        }

        this.symbolsMap.put(session, set);
        this.symbols.addAll(set);
        return true;
    }

    private boolean isValidSymbol(String symbol) {
        if (symbols.contains(symbol)) {
            return true;
        } else if (symbol.endsWith(FUT_SIGN)) {
            return futSymbols.contains(symbol.replace(FUT_SIGN, ""));
        } else {
            return spotSymbols.contains(symbol);
        }
    }

    private void checkWSConnection(String symbol) {
        if (symbols.contains(symbol)) return;
        boolean isSpot = !symbol.endsWith(FUT_SIGN);
        String rawSymbol = symbol.replace(FUT_SIGN, "");
        if (!wsClients.containsKey(symbol)) wsClients.put(symbol, new WSDepthClient(isSpot, rawSymbol));
    }
}