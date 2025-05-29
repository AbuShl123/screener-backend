package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
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
@JsonSerialize(using = TradeSerializer.class)
public class Trade implements Comparable<Trade>, Serializable {

    private double price;
    private double quantity;
    private double distance;
    private int level;
    private long life;

    @Override
    public int compareTo(Trade another) {
        int cmp = Integer.compare(level, another.level);
        if (cmp != 0) return cmp;
        cmp = Double.compare(quantity, another.quantity);
        if (cmp != 0) return cmp;
        return Double.compare(price, another.price);
    }

    public int compareToRawValues(int level, double quantity, double price) {
        int cmp = Integer.compare(this.level, level);
        if (cmp != 0) return cmp;
        cmp = Double.compare(this.quantity, quantity);
        if (cmp != 0) return cmp;
        return Double.compare(this.price, price);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Trade other = (Trade) obj;
        return Double.compare(price, other.price) == 0;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(price);
    }

    @Override
    public String toString() {
        return String.format(
                """
                ["%f","%s","%.2f",%d,%d]""",
                price, quantity, distance, level, life
        );
    }
}