package dev.abu.screener_backend.analysis;

import lombok.Getter;
import smile.math.MathEx;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DensityAnalyzer {

    private static final int FREQUENCY_OF_UPDATE = 10_000; // in millis
    private static final Map<String, DensityAnalyzer> analyzers = new HashMap<>();

    public synchronized static DensityAnalyzer getDensityAnalyzer(String symbol) {
        if (!analyzers.containsKey(symbol)) {
            analyzers.put(symbol, new DensityAnalyzer());
        }
        return analyzers.get(symbol);
    }

    private long lastUpdateTime = System.currentTimeMillis();

    @Getter private final AtomicReference<Double> firstLevel = new AtomicReference<>();
    @Getter private final AtomicReference<Double> secondLevel = new AtomicReference<>();
    @Getter private final AtomicReference<Double> thirdLevel = new AtomicReference<>();

    private DensityAnalyzer() {
    }

    public synchronized int getDensity(double data) {
        if (data < firstLevel.get()) {
            return 0;
        }

        if (data < secondLevel.get()) {
            return 1;
        }

        if (data < thirdLevel.get()) {
            return 2;
        }

        return 3;
    }

    public synchronized int getDensity(double data, double firstLevel, double secondLevel, double thirdLevel) {
        if (data < firstLevel) {
            return 0;
        }

        if (data < secondLevel) {
            return 1;
        }

        if (data < thirdLevel) {
            return 2;
        }

        return 3;
    }

    public void setLevels(double[] dataSet) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= FREQUENCY_OF_UPDATE) {
            updateLevels(dataSet);
            lastUpdateTime = currentTime;
        }
    }

    private void updateLevels(double[] dataSet) {
        double mean = MathEx.mean(dataSet);

        if (mean >= 0 && mean < 1) {
            firstLevel.set(10.0);
            secondLevel.set(100.0);
            thirdLevel.set(1000.0);
        }

        else if (mean > 0 && mean <= 1000) {
            firstLevel.set(50_000.0);
            secondLevel.set(200_000.0);
            thirdLevel.set(500_000.0);
        }

        else if (mean > 1000 && mean <= 10_000) {
            firstLevel.set(500_000.0);
            secondLevel.set(1_000_000.0);
            thirdLevel.set(10_000_000.0);
        }
    }
}
