package dev.abu.screener_backend.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

//@Controller
//@RequiredArgsConstructor
public class WSOrderBookController {

    /**
     * Endpoint for broadcasting the message to all connected clients
     * @param orderBook order book data
     */
//    @MessageMapping("/depth")
//    @SendTo("/topic/updates")
    public String broadcastOrderBook(Object orderBook) {
        return orderBook.toString();
    }
}
