package dev.abu.screener_backend.websockets;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@Slf4j
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

    @Bean(destroyMethod = "stop")
    public WebSocketClient webSocketClient() throws Exception {
        WebSocketClient client = new WebSocketClient();
        client.setInputBufferSize(65536); // 64KB
        client.setMaxTextMessageSize(512 * 1024); // 512KB
        client.start();
        return client;
    }
}