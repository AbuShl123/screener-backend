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
public class Trade {

    private double price;
    private double quantity;
    private double distance;
    private int level;
    private long life;

    public void set(double price, double quantity, double distance, int level, long life) {
        this.price = price;
        this.quantity = quantity;
        this.distance = distance;
        this.level = level;
        this.life = life;
    }

    public boolean isGreaterThan(int level, double quantity, double price) {
        if (this.level != level) {
            return this.level > level;
        }
        if (Double.compare(this.quantity, quantity) != 0) {
            return this.quantity > quantity;
        }
        return this.price > price;
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