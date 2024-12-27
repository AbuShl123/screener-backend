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
    private boolean hasSpot;
    private boolean hasFut;

    public Ticker(String symbol, boolean hasSpot, boolean hasFut) {
        this.symbol = symbol.toLowerCase();
        this.hasSpot = hasSpot;
        this.hasFut = hasFut;
    }

    public String toString() {
        return symbol;
    }

    public String getSymbol() {
        return symbol.toLowerCase();
    }
}