package dev.abu.screener_backend.analysis;

import lombok.Getter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

@Getter
public class DensityAnalyzer {

    private static final Map<String, DensityAnalyzer> analyzers = new HashMap<>();

    private final String symbol;
    private double sum;
    private long qty;

    public synchronized static DensityAnalyzer getDensityAnalyzer(String symbol) {
        if (!analyzers.containsKey(symbol)) {
            analyzers.put(symbol, new DensityAnalyzer(symbol));
        }
        return analyzers.get(symbol);
    }

    public synchronized static Collection<DensityAnalyzer> getAllDensityAnalyzers() {
        return analyzers.values();
    }

    @Getter
    private final AtomicReference<Double> firstLevel = new AtomicReference<>(-1.0);
    @Getter
    private final AtomicReference<Double> secondLevel = new AtomicReference<>(-1.0);
    @Getter
    private final AtomicReference<Double> thirdLevel = new AtomicReference<>(-1.0);

    private DensityAnalyzer(String symbol) {
        this.symbol = symbol;
    }

    public synchronized int getDensity(double data) {

        if (firstLevel.get() == -1 || secondLevel.get() == -1 || thirdLevel.get() == -1) {
            return 0;
        }

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

    public void analyzeDensities(double[] dataSet) {
        int mean = calculateMean(dataSet);
        int digits = mean == 0 ? 1 : (int) Math.pow(10, getNumOfDigits(mean));

        firstLevel.set((double) digits);
        secondLevel.set((double) digits * 10);
        thirdLevel.set((double) digits * 100);
    }

    private int calculateMean(double[] dataSet) {
        for (double v : dataSet) {
            if (v >= Double.MAX_VALUE - sum) sum = 0;
            sum += v;
            if (qty == Long.MAX_VALUE) qty = 0;
            qty++;
        }
        return (int) Math.ceil(sum / qty);
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
