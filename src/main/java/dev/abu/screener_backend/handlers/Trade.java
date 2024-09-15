package dev.abu.screener_backend.handlers;

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
    public double volume;
    public boolean isBids;
    public double incline;

    @Override
    public String toString() {
        return "[" + price + ", " + volume + ", "  + incline + ", " + isBids + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Trade t) {
            return t.price == price && t.volume == volume && t.isBids == isBids;
        }
        return false;
    }
}