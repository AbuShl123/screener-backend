package dev.abu.screener_backend.binance.entities;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@AllArgsConstructor
public class GVolume {

    private String mSymbol;
    private double gVolume;

    public void set(String mSymbol, double gVolume) {
        this.mSymbol = mSymbol;
        this.gVolume = gVolume;
    }

    @Override
    public String toString() {
        return String.format("%s=%.2f", mSymbol, gVolume);
    }
}