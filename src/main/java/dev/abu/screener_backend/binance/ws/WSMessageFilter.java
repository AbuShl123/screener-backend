package dev.abu.screener_backend.binance.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.entities.DepthEvent;
import dev.abu.screener_backend.binance.entities.KlineEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class WSMessageFilter {

    private final ObjectMapper mapper;
    private final DepthEventConsumer depthConsumer;
    private final KlineEventConsumer klineConsumer;

    private final AtomicInteger depthCount = new AtomicInteger();
    private final AtomicInteger klineCount = new AtomicInteger();

    public void filter(String json, boolean isSpot) throws IOException {
        JsonNode eField = mapper.readTree(json).get("e");
        if (eField == null || eField.isNull()) return;

        String eventType = eField.asText();
        switch (eventType) {
            case "depthUpdate":
                DepthEvent depthEvent = mapper.readValue(json, DepthEvent.class);
                depthEvent.setSpot(isSpot);
                depthConsumer.accept(depthEvent);
                depthCount.incrementAndGet();
                break;
            case "kline":
                KlineEvent klineEvent = mapper.readValue(json, KlineEvent.class);
                klineEvent.setSpot(isSpot);
                klineConsumer.accept(klineEvent);
                klineCount.incrementAndGet();
                break;
            default:
                break;
        }
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void printMetrics() {
        log.info("Received {} depth events last minute", depthCount.get());
        log.info("Received {} kline events last minute", klineCount.get());
        depthCount.set(0);
        klineCount.set(0);
    }
}
