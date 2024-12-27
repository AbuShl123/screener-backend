package dev.abu.screener_backend.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.DensityAnalyzer;
import dev.abu.screener_backend.analysis.OrderBookStream;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.entity.Trade;
import dev.abu.screener_backend.rabbitmq.RabbitMQService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static dev.abu.screener_backend.analysis.OrderBookStream.CUP_SIZE;
import static dev.abu.screener_backend.binance.WSDepthClient.FUT_SIGN;
import static dev.abu.screener_backend.utils.RequestUtilities.getQueryParams;

@Slf4j
public class ClientSession {

    private static final String INVALID_SYMBOL_MESSAGE = """
            {
            "error": "The 'symbol' parameter is invalid or missing."
            }
            """;

    private final WebSocketSession session;
    private final TickerService tickerService;
    private DensityAnalyzer densityAnalyzer;
    private DirectMessageListenerContainer container;
    private String symbol;
    private boolean isOpen;
    private int lowBound = -10;
    private int highBound = 10;

    public static void startClientSession(WebSocketSession session, RabbitMQService rabbitMQService, TickerService tickerService) {
        new ClientSession(session, rabbitMQService, tickerService);
    }

    private ClientSession(WebSocketSession session, RabbitMQService rabbitMQService, TickerService tickerService) {
        this.session = session;
        this.tickerService = tickerService;
        boolean isSuccess = setSymbol();
        if (!isSuccess) {
            sendMessage(INVALID_SYMBOL_MESSAGE);
            closeSession();
            return;
        }
        densityAnalyzer = DensityAnalyzer.getDensityAnalyzer(symbol);
        setQueryParams();
        var stream = OrderBookStream.getInstance(symbol);
        isOpen = true;
        broadCastData(stream.getBids(), stream.getAsks());
        container = rabbitMQService.createConsumer(listener(), symbol);
        log.info("Client session created - {}", this);
    }

    @Override
    public String toString() {
        return "ClientSession{" +
                "symbol='" + symbol + '\'' +
                ", lowBound=" + lowBound +
                ", highBound=" + highBound +
                '}';
    }

    public MessageListener listener() {
        return (Message message) -> {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Map<Integer, TreeSet<Trade>>> data =
                        objectMapper.readValue(message.getBody(), new TypeReference<>() {});
                var bids = data.get("bids");
                var asks = data.get("asks");
                if (session.isOpen()) {
                    broadCastData(bids, asks);
                }
            } catch (Exception e) {
                log.error("Couldn't send message to {}", session, e);
            }
        };
    }

    public void broadCastData(
            Map<Integer, TreeSet<Trade>> bids,
            Map<Integer, TreeSet<Trade>> asks
    ) {
        try {
            var bidsList = getTrades(bids);
            var asksList = getTrades(asks);

            if (!isOpen) return;
            String message = String.format("""
                    {
                    "symbol": "%s",
                    "b": %s,
                    "a": %s
                    }
                    """, symbol, bidsList, asksList);
            sendMessage(message);
        } catch (Exception e) {
            log.error("{} - Couldn't broadcast data: {}", symbol, e.getMessage());
            closeSession();
        }
    }

    public void closeSession() {
        isOpen = false;
        if (container != null && container.isRunning()) {
            container.stop();
        }
        if (session.isOpen()) {
            try {
                session.close();
            } catch (IOException e) {
                log.error("Couldn't close session", e);
            }
        }
        log.info("Closed session for: {}", session.getId());
    }

    private List<Trade> getTrades(Map<Integer, TreeSet<Trade>> tradeMap) {
        int lowLever = getLevel(lowBound);
        int highLever = getLevel(highBound);

        List<Trade> tradeList =
                tradeMap.entrySet().stream()
                        .filter(entry -> entry.getKey() >= lowLever && entry.getKey() <= highLever)
                        .flatMap(entry -> entry.getValue().stream())
                        .peek(trade -> trade.setDensity(densityAnalyzer.getDensity(trade.getQuantity())))
                        .sorted().toList();

        return new ArrayList<>(tradeList.subList(Math.max(tradeList.size() - CUP_SIZE, 0), tradeList.size()));
    }

    private int getLevel(double incline) {
        int n = (int) incline;
        int ost = n % 5;
        if (n == 0 || ost == 0) return n;
        int level;
        if (n > 0) {
            level = n + (5 - ost);
        } else {
            level = n - (5 + ost);
        }
        return level;
    }

    private void setQueryParams() {
        var queryParamsMap = getQueryParams(session);

        if (queryParamsMap.containsKey("L")) {
            lowBound = Integer.parseInt(queryParamsMap.get("L"));
        }

        if (queryParamsMap.containsKey("H")) {
            highBound = Integer.parseInt(queryParamsMap.get("H"));
        }
    }

    private boolean setSymbol() {
        var queryParamsMap = getQueryParams(session);
        String providedSymbol = queryParamsMap.get("symbol");
        if (providedSymbol == null || providedSymbol.isEmpty()) {
            return false;
        }

        List<String> savedSymbols = tickerService.getAllSymbols();
        if (!savedSymbols.contains(providedSymbol.replace(FUT_SIGN, ""))) {
            return false;
        }

        symbol = providedSymbol;
        return true;
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
