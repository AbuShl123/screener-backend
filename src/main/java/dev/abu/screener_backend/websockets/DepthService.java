package dev.abu.screener_backend.websockets;

import dev.abu.screener_backend.analysis.OrderBook;
import dev.abu.screener_backend.binance.TickerClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;

import static dev.abu.screener_backend.binance.OBManager.getOrderBook;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
@Service
public class DepthService {

    private String message;

    public void prepareData(String mSymbol) {
        OrderBook orderBook = getOrderBook(mSymbol);
        if (orderBook == null) return;
        double price = TickerClient.getPrice(mSymbol.replace(FUT_SIGN, ""));
        var bids = orderBook.getBids();
        var asks = orderBook.getAsks();
        message = String.format("""
                {
                "s": "%s",
                "p": "%f",
                "b": %s,
                "a": %s
                }
                """, mSymbol, price, bids, asks);
    }

    public void broadCastData(WebSocketSession session) {
        try {
            sendMessage(session, message);
        } catch (Exception e) {
            log.error("Couldn't broadcast data - {}: {}", e.getMessage(), message.substring(0, 20) + "...");
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
}
