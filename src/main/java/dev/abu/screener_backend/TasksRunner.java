package dev.abu.screener_backend;

import dev.abu.screener_backend.binance.DepthWebSocket;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

@Slf4j
@Component
public class TasksRunner implements CommandLineRunner {

    @Override
    public void run(String... args) throws URISyntaxException {
        String wsUrl = "wss://stream.binance.com/ws/btcusdt@depth/bnbusdt@depth/bnxusdt@depth/dogeusdt@depth";
        URI uri = new URI(wsUrl);
        log.info("wsUrl: {}", uri);
        DepthWebSocket ws = new DepthWebSocket(uri, "btcusdt", "bnbusdt", "dogeusdt", "bnxusdt");
        ws.connect();
    }

}
