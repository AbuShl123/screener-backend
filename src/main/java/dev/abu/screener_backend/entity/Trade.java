package dev.abu.screener_backend.entity;

import lombok.NoArgsConstructor;

@NoArgsConstructor
public class Trade {
    public double price;
    public double quantity;
    public double incline;
    public double density;

    public Trade(double price, double quantity, double incline, double density) {
        this.price = price;
        this.quantity = quantity;
        this.incline = incline;
        this.density = density;
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