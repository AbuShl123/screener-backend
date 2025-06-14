package dev.abu.screener_backend.analysis;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class LevelAnalyzer {

    private static final Set<String> largeTickers = new HashSet<>(Set.of("btcusdt", "btcusdt.f", "ethusdt", "ethusdt.f", "solusdt", "solusdt.f"));
    private final int[] levels;
    private final Map<Double, Integer> levelsMap = new TreeMap<>();

    public LevelAnalyzer() {
        this.levels = new int[]{500_000, 1_000_000, 3_000_000, 10_000_000};
        prepareLevelsMap();
    }

    public LevelAnalyzer(int[] levels) {
        this.levels = levels;
        prepareLevelsMap();
    }

    private void prepareLevelsMap() {
        levelsMap.put(0.5, 0);
        levelsMap.put(1.0, 1);
        levelsMap.put(2.0, 2);
        levelsMap.put(6.0, 3);
    }

    public synchronized int getLevel(double price, double qty, double distance, String mSymbol) {
        double value = price * qty;
        boolean largeTicker = largeTickers.contains(mSymbol);

        int start = 3;
        var optional = levelsMap.entrySet().stream().filter(entry -> distance <= entry.getKey()).findAny();
        if (optional.isPresent()) start = optional.get().getValue();

        for (int i = levels.length - 1; i >= start; i--) {
            int limit = largeTicker ? levels[i] * 10 : levels[i];
            if (value >= limit) return i + 1;
        }

        return 0;
    }
}