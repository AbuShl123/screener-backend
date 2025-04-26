package dev.abu.screener_backend.binance;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Table(name = "tickers")
@Getter
@Setter
@Entity
public class Ticker {

    @Id
    private String symbol;
    private double price;

    public Ticker() {
    }

    public Ticker(String symbol, double price) {
        this.symbol = symbol.toLowerCase();
        this.price = price;
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