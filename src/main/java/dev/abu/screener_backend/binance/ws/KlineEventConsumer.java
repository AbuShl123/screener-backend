package dev.abu.screener_backend.binance.ws;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.BinanceClient;
import dev.abu.screener_backend.binance.dt.TopNVolumes;
import dev.abu.screener_backend.binance.entities.GVolume;
import dev.abu.screener_backend.binance.entities.KlineData;
import dev.abu.screener_backend.binance.entities.KlineEvent;
import dev.abu.screener_backend.binance.entities.KlineInterval;
import dev.abu.screener_backend.websockets.WSOpenInterestHandler;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Component
@RequiredArgsConstructor
@Slf4j
public class KlineEventConsumer {

    private static final int CAPACITY = 30_000;
    private static final int TOP_N = 9;
    private static final String INTERVAL = KlineInterval.MIN_5.getCode();
    private static final int LIMIT = 100;

    // consumer/producer queue
    private final ArrayBlockingQueue<KlineEvent> queue = new ArrayBlockingQueue<>(CAPACITY);

    // for scheduling
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicBoolean isScheduled = new AtomicBoolean(false);

    // kline data
    private final TopNVolumes gVolume =  new TopNVolumes(TOP_N);
    private final Map<String, TreeSet<KlineData>> klines = new ConcurrentHashMap<>();
    private final Map<String, NavigableMap<Long, Double>> volumeHistory = new ConcurrentHashMap<>();
    private String klineJsonData;
    private final Object lock = new Object();

    // spring bean
    private final ObjectMapper objectMapper;
    private final WSOpenInterestHandler ws;

    @PostConstruct
    public void startConsumer() {
        Thread consumerThread = new Thread(() -> {
            while (true) {
                try {
                    process(queue.take());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        consumerThread.start();
    }

    public void accept(KlineEvent klineEvent) {
        if (!queue.offer(klineEvent)) {
            log.warn("kline queue is full!");
        }
    }

    public void fetchHistoricalData(Collection<String> symbols, boolean isSpot) {
        symbols.forEach(symbol -> fetchHistoricalData(symbol, isSpot));
    }

    private void fetchHistoricalData(String symbol, boolean isSpot) {
        try {
            String mSymbol = isSpot ? symbol : symbol + FUT_SIGN;
            NavigableMap<Long, Double> series = getVolumeHistory(mSymbol);
            TreeSet<KlineData> klineDataList = getKlines(mSymbol);

            if (series.size() >= LIMIT) return;

            String json = BinanceClient.getKlinesData(symbol, INTERVAL, LIMIT+"", isSpot);
            List<KlineData> klineList = objectMapper.readValue(json, new TypeReference<>() {});
            klineDataList.addAll(klineList);

            for (KlineData kline : klineList) {
                double volume = Double.parseDouble(kline.getVolume());
                series.put(kline.getCloseTime(), volume);
            }
        } catch (Exception e) {
            log.error("Error while fetching historical kline data: {} / {}", symbol, isSpot, e);
        }
    }

    private void process(KlineEvent klineEvent) {
        try {
            if (!klineEvent.getKline().isClosed()) return;

            if (!isScheduled.get()) {
                scheduleSendUpdate();
                isScheduled.set(true);
                log.info("Scheduled sendData() for kline");
            }

            // store the new kline
            String mSymbol = klineEvent.isSpot() ? klineEvent.getSymbol() : klineEvent.getSymbol() + FUT_SIGN;
            addNewKline(mSymbol, klineEvent.getKlineData());

            long closeTime = klineEvent.getKline().getCloseTime();
            double currentVolume = Double.parseDouble(klineEvent.getKline().getVolume());

            // Store volumes in a time-ordered map per mSymbol
            getVolumeHistory(mSymbol).put(closeTime, currentVolume);

            // Look for volume ~2 hours ago
            long twoHoursMillis = 2 * 60 * 60 * 1000L;
            long targetTime = closeTime - twoHoursMillis;

            NavigableMap<Long, Double> symbolHistory = volumeHistory.get(mSymbol);
            Map.Entry<Long, Double> pastEntry = symbolHistory.floorEntry(targetTime);

            if (pastEntry != null) {
                double pastVolume = pastEntry.getValue();
                if (pastVolume > 0) {
                    double diffPercent = ((currentVolume - pastVolume) / pastVolume) * 100.0;
                    gVolume.add(mSymbol, diffPercent);
                } else {
                    log.warn("Past volume was zero for mSymbol: {}", mSymbol);
                }
            }

            removeOldData(symbolHistory, mSymbol, closeTime);
        } catch (Exception e) {
            log.error("Error processing KlineEvent for symbol {}: {}", klineEvent.getSymbol(), e.getMessage(), e);
        }
    }

    private void scheduleSendUpdate() {
        scheduler.schedule(() -> {
            log.info("Starting to send kline data");
            sendUpdate();
            isScheduled.set(false);
        }, 2, TimeUnit.SECONDS);
    }

    private void sendUpdate() {
        try {
            List<String> topMSymbols = gVolume.getTopN().stream().map(GVolume::getMSymbol).toList();
            setKlineJsonForSymbols(topMSymbols);
            ws.broadCastData(klineJsonData);
        } catch (Exception e) {
            log.error("Error while sending Kline data for symbols: {}", e.getMessage(), e);
        }
    }

    private void setKlineJsonForSymbols(List<String> mSymbols) throws Exception {
        synchronized (lock) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("n", "kline");
            for (String mSymbol : mSymbols) {
                TreeSet<KlineData> data = getKlines(mSymbol);
                if (data != null) {
                    map.put(mSymbol, data);
                }
            }
            this.klineJsonData = objectMapper.writeValueAsString(map);
        }
    }

    public String getGVolumeJsonData() {
        synchronized (lock) {
            return klineJsonData;
        }
    }

    private void addNewKline(String mSymbol, KlineData klineData) {
        TreeSet<KlineData> klinesSet = getKlines(mSymbol);
        if (!klinesSet.add(klineData)) {
            klinesSet.remove(klineData);
            klinesSet.add(klineData);
        }
    }

    private void removeOldData(NavigableMap<Long, Double> symbolHistory, String mSymbol, long closeTime) {
        long cutoffTime = closeTime - Duration.ofHours(8).toMillis();
        symbolHistory.headMap(cutoffTime, false).clear();

        KlineData dummy = new KlineData();
        dummy.setCloseTime(cutoffTime);
        klines.get(mSymbol).headSet(dummy, false).clear();
    }

    private TreeSet<KlineData> getKlines(String mSymbol) {
        return klines.computeIfAbsent(mSymbol, s -> new TreeSet<>());
    }

    public String getCandles(String mSymbol) throws Exception {
        var candles = new TreeSet<>(getKlines(mSymbol));
        return objectMapper.writeValueAsString(candles);
    }

    private NavigableMap<Long, Double> getVolumeHistory(String mSymbol) {
        return volumeHistory.computeIfAbsent(mSymbol, s -> new ConcurrentSkipListMap<>());
    }

}