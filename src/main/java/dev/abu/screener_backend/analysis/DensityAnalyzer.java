package dev.abu.screener_backend.analysis;

public class DensityAnalyzer {

    private static final int[] LEVELS = new int[]{300_000, 500_000, 1_000_000, 10_000_000};

    public static int getDensity(double price, double qty, String symbol) {
        double value = price * qty;
        boolean largeTicker = isLarge(symbol);
        for (int i = LEVELS.length - 1; i >= 0; i--) {
            int limit = largeTicker ? LEVELS[i] * 10 : LEVELS[i];
            if (value >= limit) return i + 1;
        }
        return 0;
    }

    private static boolean isLarge(String symbol) {
        return symbol.startsWith("btcusdt") || symbol.startsWith("ethusdt") || symbol.startsWith("solusdt");
    }
}