package dev.abu.screener_backend.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Trade {
    public double price;
    public double quantity;
    public boolean isBids;
    public double incline;

    @Override
    public String toString() {
        return "[" + price + ", " + quantity + ", "  + incline + ", " + isBids + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Trade t) {
            return t.price == price && t.quantity == quantity && t.isBids == isBids;
        }
        return false;
    }
}