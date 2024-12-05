package dev.abu.screener_backend.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@Entity
@Builder
@AllArgsConstructor
public class Trade {

    @Id
    @GeneratedValue
    private Long id;
    private double price;
    private double quantity;
    private double incline;
    private double density;
    private boolean isAsk;
    private String symbol;

    public Trade(double price, double quantity, double incline, double density, boolean isAsk, String symbol) {
        this.price = price;
        this.quantity = quantity;
        this.incline = incline;
        this.density = density;
        this.isAsk = isAsk;
        this.symbol = symbol;
    }

    public Trade(double price, double quantity, boolean isAsk, String symbol) {
        this.price = price;
        this.quantity = quantity;
        this.isAsk = isAsk;
        this.symbol = symbol;
    }

    @Override
    public String toString() {
        return "[" + price + ", " + quantity + ", " + incline + ", " + density + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Trade t) {
            return t.price == price && t.quantity == quantity;
        }
        return false;
    }
}