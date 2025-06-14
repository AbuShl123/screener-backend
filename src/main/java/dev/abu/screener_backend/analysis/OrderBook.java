package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.TickerService;
import dev.abu.screener_backend.binance.depth.DepthUpdate;
import dev.abu.screener_backend.binance.depth.PriceLevel;
import dev.abu.screener_backend.websockets.SessionPool;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import static dev.abu.screener_backend.binance.depth.DepthClient.getInitialSnapshot;
import static dev.abu.screener_backend.binance.OBService.*;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;
import static dev.abu.screener_backend.utils.EnvParams.MAX_INCLINE;
import static java.lang.Double.parseDouble;
import static java.lang.Math.abs;
import static java.lang.Math.round;

@Slf4j
public class OrderBook {

    private final ObjectMapper mapper;
    private final String websocketName;
    private final boolean isSpot;
    private boolean isInitialEvent = false;

    @Getter
    private final TradeList tradeList;
    @Getter
    private final String marketSymbol;
    @Getter
    private long lastUpdateId;
    @Getter
    private boolean isTaskScheduled = false;
    @Getter
    private boolean isReSync = true;

    public OrderBook(
            String marketSymbol,
            boolean isSpot,
            String websocketName,
            SessionPool sessionPool,
            ObjectMapper mapper
    ) {
        this.marketSymbol = marketSymbol;
        this.tradeList = new TradeList(marketSymbol, sessionPool);
        this.isSpot = isSpot;
        this.websocketName = websocketName;
        this.mapper = mapper;
    }

    public boolean isScheduleNeeded() {
        return !isTaskScheduled && isReSync;
    }

    public void process(DepthUpdate depthUpdate) {
        // if re-sync is needed and there is no task that is queued for concurrent run,
        // then process this event concurrently to get the initial snapshot
        if (isScheduleNeeded()) {
            processEventConcurrently(depthUpdate);
        }

        // if initial snapshot has already been processed (in which case no task is scheduled for concurrent run),
        // then process the events as usual
        else if (!isTaskScheduled) {
            processEvent(depthUpdate);
        }

        // in any other case, events will be ignored, so they should be kept in the buffer.
    }

    private void processEventConcurrently(DepthUpdate depthUpdate) {
        isTaskScheduled = true;
        scheduleTask(() -> {
            startProcessing(depthUpdate);
            isTaskScheduled = false;
        }, isSpot);
    }

    private void startProcessing(DepthUpdate depthUpdate) {
        long U = depthUpdate.getFirstUpdateId();
        long u = depthUpdate.getFinalUpdateId();
        processInitialSnapshot(U);
        processInitialEvent(depthUpdate, U, u);
    }

    private void processEvent(DepthUpdate depthUpdate) {
        long U = depthUpdate.getFirstUpdateId();
        long u = depthUpdate.getFinalUpdateId();

        if (isInitialEvent) {
            processInitialEvent(depthUpdate, U, u);
        } else if (isSpot) {
            processSpotEvent(depthUpdate, U, u);
        } else {
            processFutEvent(depthUpdate, u);
        }
    }

    private void processInitialEvent(DepthUpdate depthUpdate, long U, long u) {
        if (lastUpdateId >= U && lastUpdateId <= u) {
            lastUpdateId = u;
            analyzeData(depthUpdate);
            isInitialEvent = false;
            incrementReSyncCount(websocketName, marketSymbol);
        } else if (U > lastUpdateId) {
            startReSync();
        }
    }

    private void processSpotEvent(DepthUpdate depthUpdate, long U, long u) {
        if (lastUpdateId + 1 >= U && lastUpdateId < u) {
            lastUpdateId = u;
            analyzeData(depthUpdate);
        } else {
            startReSync();
        }
    }

    private void processFutEvent(DepthUpdate depthUpdate, long u) {
        long pu = depthUpdate.getLastUpdateId();
        if (lastUpdateId == pu) {
            lastUpdateId = u;
            analyzeData(depthUpdate);
        } else {
            startReSync();
        }
    }

    private void startReSync() {
        isReSync = true;
        decrementReSyncCount(websocketName, marketSymbol);
        tradeList.clear();
    }

    private void processInitialSnapshot(long U) {
        String raw = null;

        try {
            lastUpdateId = 0;
            DepthUpdate snapshot;

            do {
                raw = getInitialSnapshot(marketSymbol.replace(FUT_SIGN, ""), isSpot);
                snapshot = mapper.readValue(raw, DepthUpdate.class);
                lastUpdateId = snapshot.getLastUpdateId();
            } while (lastUpdateId < U);

            analyzeData(snapshot);
            isReSync = false;
            isInitialEvent = true;

        } catch (Exception e) {
            log.error("{} Error processing snapshot: {} - {}", websocketName, raw, e.getMessage());
            isReSync = true;
        }
    }

    private void analyzeData(DepthUpdate depthUpdate) {
        List<PriceLevel> asks = depthUpdate.getAsks();
        List<PriceLevel> bids = depthUpdate.getBids();
        analyze(depthUpdate, asks, bids);
    }

    private void analyze(DepthUpdate depthUpdate, List<PriceLevel> asks, List<PriceLevel> bids) {
        long timestamp = getTimeStamp(depthUpdate);
        traverseArray(asks, timestamp, true);
        traverseArray(bids, timestamp, false);
    }

    private long getTimeStamp(DepthUpdate depthUpdate) {
        long e = depthUpdate.getEventTime();
        return e == 0 ? System.currentTimeMillis() : e;
    }

    private void traverseArray(List<PriceLevel> array, long timestamp, boolean isAsk) {
        for (PriceLevel priceLevel : array) {
            double price = parseDouble(priceLevel.price());
            double quantity = parseDouble(priceLevel.quantity());
            double distance = getDistance(price);
            if (distance <= MAX_INCLINE) {
                tradeList.addTrade(price, quantity, distance, isAsk, timestamp);
            }
        }
    }

    private double getDistance(double price) {
        double marketPrice = TickerService.getPrice(marketSymbol);
        double distance = abs((price - marketPrice) / marketPrice * 100);
        return round(distance * 100.0) / 100.0;
    }
}