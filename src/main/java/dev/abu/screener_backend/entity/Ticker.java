package dev.abu.screener_backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@Entity
public class Ticker {

    @Id
    @GeneratedValue
    private Long id;
    private String symbol;

    public Ticker(String symbol) {
        this.symbol = symbol.toLowerCase();
    }

    public static Ticker of(String symbol) {
        return new Ticker(symbol);
    }

    public String toString() {
        return symbol;
    }

    public String getSymbol() {
        return symbol.toLowerCase();
    }
}