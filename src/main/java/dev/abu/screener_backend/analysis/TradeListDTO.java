package dev.abu.screener_backend.analysis;

import java.util.Collection;

public record TradeListDTO(String s, Collection<Trade> b, Collection<Trade> a) {
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        TradeListDTO other = (TradeListDTO) obj;
        return this.s.equals(other.s);
    }
}
