package dev.abu.screener_backend.binance;

import java.util.Collection;

/**
 * Record object used as POJO for sending messages to Binance websockets.
 * @param method type of subscription: "SUBSCRIBE" will subscribe to new streams, "UNSUBSCRIBE" will remove current subscriptions.
 * @param params streams to connect for. For example: ["btcusdt@depth", "bnbusdt@depth"]
 * @param id string that represents 32-bit number.
 */
public record BinanceSubscriptionRequest(
        String method,
        Collection<String> params,
        String id
) {}
