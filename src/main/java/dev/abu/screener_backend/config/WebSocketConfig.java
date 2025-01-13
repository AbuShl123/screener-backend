package dev.abu.screener_backend.config;

import dev.abu.screener_backend.handlers.WSOpenInterestHandler;
import dev.abu.screener_backend.handlers.WSOrderBookHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WSOrderBookHandler orderBookHandler;
    private final WSOpenInterestHandler openInterestHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(orderBookHandler, "/binance/depth").setAllowedOrigins("*")
                .addHandler(openInterestHandler, "/bitget/openInterest").setAllowedOrigins("*");
    }
}