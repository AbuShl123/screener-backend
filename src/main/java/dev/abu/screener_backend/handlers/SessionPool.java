package dev.abu.screener_backend.handlers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static dev.abu.screener_backend.binance.BinanceService.isSymbolConnected;
import static dev.abu.screener_backend.binance.BinanceService.waitFor;
import static dev.abu.screener_backend.utils.RequestUtilities.getQueryParams;

@Component
@Slf4j
@RequiredArgsConstructor
public class SessionPool {

    private final DepthService depthService;
    private final Set<WebSocketSession> sessions = new HashSet<>();
    private final Map<String, Set<WebSocketSession>> symbolsMap = new HashMap<>();
    private final Set<String> symbolsFlat = new HashSet<>();

    public void addSession(WebSocketSession session) {
        extractInputData(session);
        sessions.add(session);
    }

    public void removeClosedSessions() {
        sessions.removeIf(session -> !session.isOpen());
        symbolsFlat.removeIf(symbol -> symbolsMap.get(symbol).isEmpty());
    }

    public void sendData() {
        if (sessions.isEmpty()) return;

        // to avoid concurrent modification exception
        Set<String> symbols = new HashSet<>(this.symbolsFlat);

        for (String symbol : symbols) {
            depthService.prepareData(symbol);
            symbolsMap.get(symbol).forEach(depthService::broadCastData);
            waitFor(100);
        }
    }

    private void extractInputData(WebSocketSession session) {
        var queryParamsMap = getQueryParams(session);
        String symbolsStr = queryParamsMap.get("symbols");
        if (symbolsStr == null || symbolsStr.isEmpty()) return;
        String[] rawSymbols = symbolsStr.split("/");

        for (String rawSymbol : rawSymbols) {
            String symbol = rawSymbol.trim().toLowerCase();
            if (!isSymbolConnected(symbol)) continue;
            if (!symbolsMap.containsKey(symbol)) symbolsMap.put(symbol, new HashSet<>());
            symbolsMap.get(symbol).add(session);
            this.symbolsFlat.add(symbol);
        }
    }
}