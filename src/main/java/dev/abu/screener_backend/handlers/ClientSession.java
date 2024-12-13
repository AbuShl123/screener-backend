package dev.abu.screener_backend.handlers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.analysis.DensityAnalyzer;
import dev.abu.screener_backend.analysis.OrderBookStream;
import dev.abu.screener_backend.binance.jpa.TickerService;
import dev.abu.screener_backend.entity.Trade;
import dev.abu.screener_backend.rabbitmq.RabbitMQService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageListener;
import org.springframework.amqp.rabbit.listener.DirectMessageListenerContainer;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static dev.abu.screener_backend.analysis.OrderBookStream.CUP_SIZE;
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
    private AtomicReference<Double> firstLevel;
    private AtomicReference<Double> secondLevel;
    private AtomicReference<Double> thirdLevel;

    public ClientSession(WebSocketSession session, RabbitMQService rabbitMQService, TickerService tickerService) {
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
        broadCastData(stream.getBids(), stream.getAsks());
        container = rabbitMQService.createClientConsumer(listener(), symbol);
        isOpen = true;
        log.info("Client session is created for {}", symbol);
    }

    public MessageListener listener() {
        return (Message message) -> {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                Map<String, Map<Integer, TreeSet<Trade>>> data =
                        objectMapper.readValue(message.getBody(), new TypeReference<>() {});
                var bids = data.get("bids");
                var asks = data.get("asks");
                broadCastData(bids, asks);
            } catch (Exception e) {
                log.error("Couldn't send message to {}", session, e);
            }
        };
    }

    public void broadCastData(
            Map<Integer, TreeSet<Trade>> bids,
            Map<Integer, TreeSet<Trade>> asks
    ) {
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
    }

    public boolean isOpen() {
        return isOpen;
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
                        .peek(trade ->
                                trade.setDensity(
                                        densityAnalyzer.getDensity(
                                                trade.getQuantity(),
                                                firstLevel.get(),
                                                secondLevel.get(),
                                                thirdLevel.get()
                                        )
                        ))
                        .sorted().toList();

        List<Trade> orderBook = new ArrayList<>(tradeList.subList(Math.max(tradeList.size() - CUP_SIZE, 0), tradeList.size()));
        Collections.reverse(orderBook);
        return orderBook;
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
        firstLevel = densityAnalyzer.getFirstLevel();
        secondLevel = densityAnalyzer.getSecondLevel();
        thirdLevel = densityAnalyzer.getThirdLevel();

        if (queryParamsMap.containsKey("L")) {
            lowBound = Integer.parseInt(queryParamsMap.get("L"));
        }

        if (queryParamsMap.containsKey("H")) {
            highBound = Integer.parseInt(queryParamsMap.get("H"));
        }

        if (queryParamsMap.containsKey("lev1")) {
            firstLevel = new AtomicReference<>(Double.parseDouble(queryParamsMap.get("lev1")));
        }

        if (queryParamsMap.containsKey("lev2")) {
            secondLevel = new AtomicReference<>(Double.parseDouble(queryParamsMap.get("lev2")));
        }

        if (queryParamsMap.containsKey("lev3")) {
            thirdLevel = new AtomicReference<>(Double.parseDouble(queryParamsMap.get("lev3")));
        }
    }

    private boolean setSymbol() {
        var queryParamsMap = getQueryParams(session);
        String providedSymbol = queryParamsMap.get("symbol");
        if (providedSymbol == null || providedSymbol.isEmpty()) {
            return false;
        }

        List<String> savedSymbols = tickerService.getAllSymbols();
        if (!savedSymbols.contains(providedSymbol)) {
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
