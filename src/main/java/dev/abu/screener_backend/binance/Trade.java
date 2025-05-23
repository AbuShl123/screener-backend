package dev.abu.screener_backend.binance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

import static java.lang.Math.abs;

@AllArgsConstructor
@NoArgsConstructor
@Setter
@Getter
public class Trade implements Comparable<Trade>, Serializable {

    private double price;
    private double quantity;
    private double distance;
    private int level;
    private long life;

    @Override
    public int compareTo(Trade another) {
        double t1 = getLevel();
        double t2 = another.getLevel();
        if (t1 == t2) return 1;
        return (int) (t1 - t2);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Trade t) {
            return t.price == price;
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format(
                """
                [
                "%f",
                "%s",
                "%.2f",
                %d,
                %d
                ]""",

                price, quantity, distance, level, life
        );
    }
}