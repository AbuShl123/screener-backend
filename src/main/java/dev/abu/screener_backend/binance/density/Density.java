package dev.abu.screener_backend.binance.density;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "density")
public class Density {

    @EmbeddedId
    private DensityId id;

    private double qty;
    private boolean isAsk;
    private long life;
    private int statecode;

    public Density(DensityId id, double qty, boolean isAsk, long life) {
        this.id = id;
        this.qty = qty;
        this.isAsk = isAsk;
        this.life = life;
    }
}
