package dev.abu.screener_backend.analysis;

import dev.abu.screener_backend.binance.Ticker;
import smile.math.MathEx;

import java.time.Duration;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.Map;

public class DensityAnalyzer {


    /** Analyzers per ticker type. */
    private static Map<Ticker, DensityAnalyzer> analyzers = new HashMap<>();

    /** frequency of density level update (in seconds). */
    private static int FREQUENCY_OF_UPDATE = 180;

    public static DensityAnalyzer get(Ticker ticker) {
        if (analyzers.containsKey(ticker)) {
            return analyzers.get(ticker);
        } else {
            analyzers.put(ticker, new DensityAnalyzer());
            return get(ticker);
        }
    }

    private LocalTime lastUpdateTime;
    private double firstLevel;
    private double secondLevel;
    private double thirdLevel;

    private DensityAnalyzer() {
    }

    public int getDensity(double data) {

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
        if (lastUpdateTime == null) {
            updateLevels(dataSet);
        }

        var duration = Duration.between(lastUpdateTime, LocalTime.now());

        if (duration.toSeconds() >= FREQUENCY_OF_UPDATE) {
            updateLevels(dataSet);
        }
    }

    private void updateLevels(double[] dataSet) {
        double mean = MathEx.mean(dataSet);

        if (mean >= 0 && mean < 1) {
            firstLevel = 10;
            secondLevel = 100;
            thirdLevel = 1000;
        }

        else if (mean > 0 && mean <= 1000) {
            firstLevel = 50_000;
            secondLevel = 200_000;
            thirdLevel = 500_000;
        }

        else if (mean > 1000 && mean <= 10_000) {
            firstLevel = 500_000;
            secondLevel = 1_000_000;
            thirdLevel = 10_000_000;
        }

        lastUpdateTime = LocalTime.now();
    }
}
