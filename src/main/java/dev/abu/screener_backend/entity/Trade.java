package dev.abu.screener_backend.entity;

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

    private String price;
    private double quantity;
    private double incline;
    private int density;
    private long life;

    @Override
    public int compareTo(Trade another) {
        double qty1 = getQuantity();
        double qty2 = another.getQuantity();
        if (qty1 == qty2) return 1;
        return (int) (qty1 - qty2);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof Trade t) {
            return t.price.equals(price);
        }
        return false;
    }

    @Override
    public String toString() {
        return String.format(
                """
                [
                "%s",
                "%s",
                "%.2f",
                %d,
                %d
                ]""",

                price, quantity, incline, density, life
        );
    }
}