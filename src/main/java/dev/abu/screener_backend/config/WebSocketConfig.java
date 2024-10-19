package dev.abu.screener_backend.config;

import dev.abu.screener_backend.handlers.WSOrderBookHandler;
import dev.abu.screener_backend.interceptors.WSOrderBookInterceptor;
import lombok.AllArgsConstructor;
import org.antlr.v4.runtime.atn.SemanticContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.HttpSessionHandshakeInterceptor;

@Configuration
@EnableWebSocket
@AllArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    public static final String ORDER_BOOK_PATH = "/orderbook/{ticker}";

    private final WSOrderBookHandler orderBookHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Register the WebSocket endpoint
        registry.addHandler(orderBookHandler, ORDER_BOOK_PATH)
                .setAllowedOrigins("*")
                .addInterceptors(new WSOrderBookInterceptor());
    }
}