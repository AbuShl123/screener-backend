package dev.abu.screener_backend.config;

import dev.abu.screener_backend.websockets.WSDepthHandler;
import dev.abu.screener_backend.websockets.WSOpenInterestHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandshakeInterceptor handshakeInterceptor;
    private final WSOpenInterestHandler openInterestHandler;
    private final WSDepthHandler depthHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(depthHandler, "/ws/binance/depth").setAllowedOrigins("*")
                .addHandler(openInterestHandler, "/ws/bitget/openInterest").setAllowedOrigins("*")
                .addInterceptors(handshakeInterceptor);
    }
}