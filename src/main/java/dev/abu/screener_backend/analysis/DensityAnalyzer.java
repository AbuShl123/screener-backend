package dev.abu.screener_backend.analysis;

import lombok.Getter;

@Getter
public class DensityAnalyzer {

    //    private static final Map<Double, Integer[]> levelsMap = new TreeMap<>();
    private static final int[] levels;

    static {
        levels = new int[]{300_000, 500_000, 1_000_000, 10_000_000};
//        levelsMap.put(1.0, new Integer[]{
//                300_000, 500_000, 1_000_000
//        });
//
//        levelsMap.put(2.0, new Integer[]{
//                500_000, 800_000, 1_000_000
//        });
//
//        levelsMap.put(5.0, new Integer[]{
//                800_000, 1_000_000, 10_000_000
//        });
//
//        levelsMap.put(10.0, new Integer[]{
//                800_000, 1_000_000, 10_000_000
//        });
    }

    public static synchronized int getDensity(double price, double qty, double incline, String symbol) {
        double value = price * qty;
        boolean largeTicker = symbol.contains("btcusdt") || symbol.contains("ethusdt");
        for (int i = levels.length - 1; i >= 0; i--) {
            int limit = largeTicker ? levels[i] * 10 : levels[i];
            if (value >= limit) return i + 1;
        }
        return 0;
    }
}
