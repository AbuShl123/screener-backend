package dev.abu.screener_backend.binance.dt;

import dev.abu.screener_backend.binance.entities.GVolume;

import java.util.*;

public class TopNVolumes {

    private final int capacity;
    private final Map<String, GVolume> gvMap;
    private final TreeSet<GVolume> gvRating;

    public TopNVolumes(int capacity) {
        this.capacity = capacity;
        gvMap = new HashMap<>(capacity);
        gvRating = new TreeSet<>(
                Comparator.comparing(GVolume::getGVolume)
                .thenComparing(GVolume::getMSymbol)
        );
    }

    public boolean add(String mSymbol, double volume) {
        if (gvMap.containsKey(mSymbol)) {
            GVolume v = gvMap.get(mSymbol);
            gvRating.remove(v);
            v.setGVolume(volume);
            gvRating.add(v);
            return true;
        }

        if (gvRating.size() < capacity) {
            GVolume v = new GVolume(mSymbol, volume);
            gvMap.put(mSymbol, v);
            gvRating.add(v);
            return true;
        }

        GVolume smallest = gvRating.first();
        if (volume <= smallest.getGVolume()) {
            return false;
        }

        gvMap.remove(smallest.getMSymbol());
        gvRating.remove(smallest);
        smallest.set(mSymbol, volume);
        gvMap.put(mSymbol, smallest);
        gvRating.add(smallest);
        return true;
    }

    public List<GVolume> getTopN() {
        return new ArrayList<>(gvRating);
    }

    @Override
    public String toString() {
        return getTopN().toString();
    }
}
