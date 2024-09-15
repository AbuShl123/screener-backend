package dev.abu.screener_backend.config;

import dev.abu.screener_backend.handlers.WSOrderBookHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final WSOrderBookHandler orderBookHandler;

    public WebSocketConfig(WSOrderBookHandler orderBookHandler) {
        this.orderBookHandler = orderBookHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register the WebSocket endpoint
        registry.addHandler(orderBookHandler, "/orderbook/{symbol}")
                .setAllowedOrigins("*")
                .addInterceptors(new HttpSessionHandshakeInterceptor());
    }
}