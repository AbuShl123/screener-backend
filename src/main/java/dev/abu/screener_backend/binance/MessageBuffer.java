package dev.abu.screener_backend.binance;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Consumer;

@Slf4j
public class MessageBuffer<E> {

    private final ScheduledExecutorService execService = Executors.newSingleThreadScheduledExecutor();
    private final BlockingQueue<E> messageQueue = new LinkedBlockingQueue<>(1000);

    public void startProcessing(Consumer<E> handler) {
        execService.scheduleAtFixedRate(() -> {
            List<E> batch = new ArrayList<>();
            messageQueue.drainTo(batch, 50);
            batch.forEach(handler);
        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    public void buffer(E message) {
        if (!messageQueue.offer(message)) {
            log.warn("Buffer Overflow!!! - it is fucked up now.");
        }
    }

    public int size() {
        return messageQueue.size();
    }
}