package dev.abu.screener_backend.binance.entities;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum KlineInterval {
    MIN("1m"),
    MIN_5("5m");

    private final String code;

    public static KlineInterval fromCode(String code) {
        for (KlineInterval interval : values()) {
            if (interval.code.equals(code)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("Unknown code: " + code);
    }
}

