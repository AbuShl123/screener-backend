package dev.abu.screener_backend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.BinanceOrderBookClient;
import dev.abu.screener_backend.binance.Ticker;
import smile.math.MathEx;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class Main {
    public static void main(String[] args) {

        BinanceOrderBookClient binance = new BinanceOrderBookClient(Ticker.BTCUSDT);
        String payload = binance.getData();
        Set<Double> bidsSet = new HashSet<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(payload);
            JsonNode bids = jsonNode.get("bids");
            for (JsonNode bid : bids) {
                bidsSet.add(bid.get(1).asDouble());
            }
        } catch (JsonProcessingException e) {
            System.out.println("Something went wrong");
            e.printStackTrace();
        }

        double[] dataSet = new double[bidsSet.size()];
        int i = 0;
        for (Double v : bidsSet) {
            dataSet[i++] = v;
        }
        System.out.println(Arrays.toString(dataSet));

        double mean = MathEx.mean(dataSet);
        System.out.println(mean);
        double max = MathEx.max(dataSet);
        System.out.println(max);
    }
}
