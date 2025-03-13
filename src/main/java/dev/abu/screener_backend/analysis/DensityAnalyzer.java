package dev.abu.screener_backend.analysis;

public class DensityAnalyzer {

    private static final int[] levels = new int[]{300_000, 500_000, 1_000_000, 10_000_000};

    public static synchronized int getDensity(double price, double qty, String symbol) {
        double value = price * qty;
        boolean largeTicker = symbol.startsWith("btcusdt") || symbol.startsWith("ethusdt") || symbol.startsWith("solusdt");
        for (int i = levels.length - 1; i >= 0; i--) {
            int limit = largeTicker ? levels[i] * 10 : levels[i];
            if (value >= limit) return i + 1;
        }
        return 0;
    }
}