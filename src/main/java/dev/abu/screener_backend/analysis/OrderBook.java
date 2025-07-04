package dev.abu.screener_backend.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.depth.DepthUpdate;
import dev.abu.screener_backend.websockets.EventDistributor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

import static dev.abu.screener_backend.binance.OBService.decrementReSyncCount;
import static dev.abu.screener_backend.binance.OBService.incrementReSyncCount;
import static dev.abu.screener_backend.binance.depth.DepthClient.getInitialSnapshot;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
public class OrderBook {

    private final ObjectMapper mapper;
    private final EventDistributor eventDistributor;
    private final String websocketName;
    private final boolean isSpot;
    private boolean isInitialEvent = false;

    @Getter
    private final GeneralTradeList generalTradeList;
    @Getter
    private final Set<TradeList> tls;
    @Getter
    private final String mSymbol;
    @Getter
    private long lastUpdateId;
    @Getter
    private boolean isTaskScheduled = false;
    private boolean isReSync = true;

    public OrderBook(
            String mSymbol,
            boolean isSpot,
            String websocketName,
            ObjectMapper mapper,
            EventDistributor eventDistributor
    ) {
        this.mSymbol = mSymbol;
        this.isSpot = isSpot;
        this.websocketName = websocketName;
        this.mapper = mapper;
        this.eventDistributor = eventDistributor;
        this.generalTradeList = new GeneralTradeList();
        this.tls = new HashSet<>();
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
        AsyncOBScheduler.scheduleTask(() -> {
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
            analyzeData(depthUpdate, false);
            isInitialEvent = false;
            incrementReSyncCount(websocketName, mSymbol);
        } else if (U > lastUpdateId) {
            startReSync();
        }
    }

    private void processSpotEvent(DepthUpdate depthUpdate, long U, long u) {
        if (lastUpdateId + 1 >= U && lastUpdateId < u) {
            lastUpdateId = u;
            analyzeData(depthUpdate, false);
        } else {
            startReSync();
        }
    }

    private void processFutEvent(DepthUpdate depthUpdate, long u) {
        long pu = depthUpdate.getLastUpdateId();
        if (lastUpdateId == pu) {
            lastUpdateId = u;
            analyzeData(depthUpdate, false);
        } else {
            startReSync();
        }
    }

    private void startReSync() {
        isReSync = true;
        decrementReSyncCount(websocketName, mSymbol);
        generalTradeList.clear();
    }

    private void processInitialSnapshot(long U) {
        String raw = null;

        try {
            lastUpdateId = 0;
            DepthUpdate snapshot;

            do {
                raw = getInitialSnapshot(mSymbol.replace(FUT_SIGN, ""), isSpot);
                snapshot = mapper.readValue(raw, DepthUpdate.class);
                lastUpdateId = snapshot.getLastUpdateId();
            } while (lastUpdateId < U);

            analyzeData(snapshot, true);
            isReSync = false;
            isInitialEvent = true;

        } catch (Exception e) {
            log.error("{} Error processing snapshot: {} - {}", websocketName, raw, e.getMessage());
            isReSync = true;
        }
    }

    private synchronized void analyzeData(DepthUpdate depthUpdate, boolean initialSnapshot) {
        generalTradeList.process(depthUpdate.getBids(), depthUpdate.getAsks(), initialSnapshot);
        tls.forEach(tl -> {
            tl.process(generalTradeList);
            if (tl.getMaxLevel() >= 1 || tl.getPrevMaxLevel() >= 1) {
                eventDistributor.distribute(tl.getSettings().getSettingsHash(), tl.getMaxLevel(), tl.toDTO());
            }
        });
    }

    public synchronized void addTL(TradeList tradeList) {
        tls.add(tradeList);
    }

    public synchronized void removeTL(TradeList tradeList) {
        tls.remove(tradeList);
    }
}