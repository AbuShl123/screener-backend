package dev.abu.screener_backend.binance;

import lombok.AllArgsConstructor;

import java.io.Serializable;

@AllArgsConstructor
public class OrderBookRawData implements Serializable {
    public String data;
}