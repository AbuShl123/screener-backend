package dev.abu.screener_backend.analysis;

import lombok.Getter;
import smile.math.MathEx;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

public class DensityAnalyzer {

    public static final int UPDATE_FREQUENCY = 50;
    private static final Map<String, DensityAnalyzer> analyzers = new HashMap<>();

    private final String symbol;
    private int counter = 40;

    public synchronized static DensityAnalyzer getDensityAnalyzer(String symbol) {
        if (!analyzers.containsKey(symbol)) {
            analyzers.put(symbol, new DensityAnalyzer(symbol));
        }
        return analyzers.get(symbol);
    }

    @Getter
    private final AtomicReference<Double> firstLevel = new AtomicReference<>();
    @Getter
    private final AtomicReference<Double> secondLevel = new AtomicReference<>();
    @Getter
    private final AtomicReference<Double> thirdLevel = new AtomicReference<>();

    private DensityAnalyzer(String symbol) {
        this.symbol = symbol;
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

    public boolean analyzeDensities(double[] dataSet) {
        if (counter < UPDATE_FREQUENCY) {
            counter++;
            return false;
        }
        counter = 0;

        int mean = (int) MathEx.mean(dataSet);
        int digits = mean == 0 ? 1 : (int) Math.pow(10, getNumOfDigits(mean));

        firstLevel.set(digits * 1.0);
        secondLevel.set(digits * 10.0);
        thirdLevel.set(digits * 100.0);
        return true;
    }

    private int getNumOfDigits(int num) {
        int digits = 0;
        while (num > 0) {
            digits++;
            num /= 10;
        }
        return digits;
    }

    @Override
    public String toString() {
        return "DensityAnalyzer: {symbol=" + symbol + ", firstLevel=" + firstLevel.get() + ", secondLevel=" + secondLevel.get() + ", thirdLevel=" + thirdLevel.get() + "}";
    }
}
