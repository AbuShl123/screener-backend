package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;

public class DensityAnalyzer {

    private static final int[] LEVELS = new int[]{500_000, 1_000_000, 3_000_000, 10_000_000};

    private static final Map<Double, Integer> levelsMap = new TreeMap<>();

    static {
        levelsMap.put(0.5, 0);
        levelsMap.put(1.0, 1);
        levelsMap.put(2.0, 2);
        levelsMap.put(6.0, 3);
    }

    public static void main(String[] args) throws IOException {
        String response = Files.readString(Paths.get("C:\\git\\screener-backend\\data.json"));
        ObjectMapper mapper = new ObjectMapper();
        JsonNode root = mapper.readTree(response);

        for (JsonNode obj : root) {
            String symbol = obj.get("symbol").asText();
            double maxBidQty = obj.get("maxBidQty").asDouble();
            double maxAskQty = obj.get("maxAskQty").asDouble();
            double price = obj.get("price").asDouble();

        }
    }

    public static synchronized int getLevel(double price, double qty, double distance, String symbol) {
        double value = price * qty;
        boolean largeTicker = isLarge(symbol);
        final double dist = truncateAfterFirstDecimal(Math.abs(distance));

        int start = 3;
        var optional = levelsMap.entrySet().stream().filter(entry -> dist <= entry.getKey()).findAny();
        if (optional.isPresent()) start = optional.get().getValue();

        for (int i = LEVELS.length - 1; i >= start; i--) {
            int limit = largeTicker ? LEVELS[i] * 10 : LEVELS[i];
            if (value >= limit) return i + 1;
        }

        return 0;
    }

    public static double truncateAfterFirstDecimal(double value) {
        return Math.floor(value * 10) / 10.0;
    }

    private static boolean isLarge(String symbol) {
        return symbol.startsWith("btcusdt") || symbol.startsWith("ethusdt") || symbol.startsWith("solusdt");
    }
}