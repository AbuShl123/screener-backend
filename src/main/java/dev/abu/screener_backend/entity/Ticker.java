package dev.abu.screener_backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;

@Table(name = "tickers")
@Getter
@Setter
@Entity
public class Ticker {

    @Id
    private String symbol;
    private double price;
    private boolean hasSpot;
    private boolean hasFut;

    public Ticker() {
    }

    public Ticker(String symbol, double price, boolean hasSpot, boolean hasFut) {
        this.symbol = symbol.toLowerCase();
        this.price = price;
        this.hasSpot = hasSpot;
        this.hasFut = hasFut;
    }

    @Override
    public String toString() {
        return symbol;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Ticker ticker = (Ticker) obj;
        return symbol.equalsIgnoreCase(ticker.symbol);
    }

    @Override
    public int hashCode() {
        return symbol.hashCode();
    }
}