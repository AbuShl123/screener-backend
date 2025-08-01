package dev.abu.screener_backend.binance.ws;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;

@Slf4j
public class WSMessageHandler {

    private static final int CAPACITY = 30_000;
    private final WSMessageFilter filter;
    private final boolean isSpot;
    private final ArrayBlockingQueue<String> queue;

    public WSMessageHandler(WSMessageFilter filter, boolean isSpot) {
        this.filter = filter;
        this.isSpot = isSpot;
        this.queue = new ArrayBlockingQueue<>(CAPACITY, false);
        startConsumer();
    }

    public void startConsumer() {
        Thread consumerThread = new Thread(() -> {
            while (true) {
                try {
                    String message = queue.take();
                    filter.filter(message, isSpot);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException e) {
                    log.error("Exception in consumer thread", e);
                }
            }
        });
        consumerThread.start();
    }

    public void handleMessage(String msg) {
        try {
            take(msg);
        } catch (Exception e) {
            log.error("Error handling message: {}", e.getMessage());
        }
    }

    private void take(String msg) {
        boolean success = queue.offer(msg);
        if (!success) {
            log.warn("queue is full!");
            queue.clear();
        }
    }
}
