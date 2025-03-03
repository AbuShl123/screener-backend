package dev.abu.screener_backend.analysis;

import lombok.Getter;

@Getter
public class DensityAnalyzer {

    private static final double[] levels;

    static {
        levels = new double[20];
        levels[0] = 0.5;
        levels[1] = 300_000;

        levels[4] = 1.0;
        levels[5] = 500_000;

        levels[8] = 2.0;
        levels[9] = 1_000_000;

        levels[12] = 5.0;
        levels[13] = 10_000_000;

        levels[16] = 10.0;
        levels[17] = 10_000_000;
    }

    public static synchronized int getDensity(double price, double qty, double incline, String symbol) {
        double percentage = Math.round(Math.abs(incline));
        double value = price * qty;
        boolean largeTicker = symbol.contains("btcusdt") || symbol.contains("ethusdt");

        for (int i = 0; i < levels.length; i += 4) {
            double distance = largeTicker ? levels[i] / 2 : levels[i];
            if (percentage <= distance) {
                return getDensity(value, i, largeTicker);
            }
        }

        return 0;
    }

    private static int getDensity(double data, int i, boolean largeTicker) {
        double level3 = largeTicker ? levels[i + 1] * 10 : levels[i + 1];
        double level2 = largeTicker ? levels[i + 2] * 10 : levels[i + 2];
        double level1 = largeTicker ? levels[i + 3] * 10 : levels[i + 3];
        if (data >= level3) return 3;
        if (data >= level2) return 2;
        if (data >= level1) return 1;
        else return 0;
    }
}
