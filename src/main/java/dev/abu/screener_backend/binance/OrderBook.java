package dev.abu.screener_backend.binance;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.abu.screener_backend.binance.dt.AsyncOBScheduler;
import dev.abu.screener_backend.binance.dt.GeneralTradeList;
import dev.abu.screener_backend.binance.dt.TradeList;
import dev.abu.screener_backend.binance.entities.DepthEvent;
import dev.abu.screener_backend.websockets.EventDistributor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.HashSet;
import java.util.Set;

import static dev.abu.screener_backend.binance.OBService.decrementReSyncCount;
import static dev.abu.screener_backend.binance.OBService.incrementReSyncCount;
import static dev.abu.screener_backend.binance.BinanceClient.getInitialSnapshot;
import static dev.abu.screener_backend.utils.EnvParams.FUT_SIGN;

@Slf4j
public class OrderBook {

    private final ObjectMapper mapper;
    private final EventDistributor eventDistributor;
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
            ObjectMapper mapper,
            EventDistributor eventDistributor
    ) {
        this.mSymbol = mSymbol;
        this.isSpot = isSpot;
        this.mapper = mapper;
        this.eventDistributor = eventDistributor;
        this.generalTradeList = new GeneralTradeList();
        this.tls = new HashSet<>();
    }

    public boolean isScheduleNeeded() {
        return !isTaskScheduled && isReSync;
    }

    public void process(DepthEvent depthEvent) {
        // if re-sync is needed and there is no task that is queued for concurrent run,
        // then process this event concurrently to get the initial snapshot
        if (isScheduleNeeded()) {
            processEventConcurrently(depthEvent);
        }

        // if initial snapshot has already been processed (in which case no task is scheduled for concurrent run),
        // then process the events as usual
        else if (!isTaskScheduled) {
            processEvent(depthEvent);
        }

        // in any other case, events will be ignored, so they should be kept in the buffer.
    }

    private void processEventConcurrently(DepthEvent depthEvent) {
        isTaskScheduled = true;
        AsyncOBScheduler.scheduleTask(() -> {
            startProcessing(depthEvent);
            isTaskScheduled = false;
        }, isSpot);
    }

    private void startProcessing(DepthEvent depthEvent) {
        long U = depthEvent.getFirstUpdateId();
        long u = depthEvent.getFinalUpdateId();
        processInitialSnapshot(U);
        processInitialEvent(depthEvent, U, u);
    }

    private void processEvent(DepthEvent depthEvent) {
        long U = depthEvent.getFirstUpdateId();
        long u = depthEvent.getFinalUpdateId();

        if (isInitialEvent) {
            processInitialEvent(depthEvent, U, u);
        } else if (isSpot) {
            processSpotEvent(depthEvent, U, u);
        } else {
            processFutEvent(depthEvent, u);
        }
    }

    private void processInitialEvent(DepthEvent depthEvent, long U, long u) {
        if (lastUpdateId >= U && lastUpdateId <= u) {
            lastUpdateId = u;
            analyzeData(depthEvent, false);
            isInitialEvent = false;
            incrementReSyncCount(isSpot, mSymbol);
        } else if (U > lastUpdateId) {
            startReSync();
        }
    }

    private void processSpotEvent(DepthEvent depthEvent, long U, long u) {
        if (lastUpdateId + 1 >= U && lastUpdateId < u) {
            lastUpdateId = u;
            analyzeData(depthEvent, false);
        } else {
            startReSync();
        }
    }

    private void processFutEvent(DepthEvent depthEvent, long u) {
        long pu = depthEvent.getLastUpdateId();
        if (lastUpdateId == pu) {
            lastUpdateId = u;
            analyzeData(depthEvent, false);
        } else {
            startReSync();
        }
    }

    private void startReSync() {
        isReSync = true;
        decrementReSyncCount(isSpot, mSymbol);
        generalTradeList.clear();
    }

    private void processInitialSnapshot(long U) {
        String raw = null;

        try {
            lastUpdateId = 0;
            DepthEvent snapshot;

            do {
                raw = getInitialSnapshot(mSymbol.replace(FUT_SIGN, ""), isSpot);
                snapshot = mapper.readValue(raw, DepthEvent.class);
                lastUpdateId = snapshot.getLastUpdateId();
            } while (lastUpdateId < U);

            analyzeData(snapshot, true);
            isReSync = false;
            isInitialEvent = true;

        } catch (Exception e) {
            log.error("Error processing snapshot: {} - {}", raw, e.getMessage());
            isReSync = true;
        }
    }

    private synchronized void analyzeData(DepthEvent depthEvent, boolean initialSnapshot) {
        generalTradeList.process(depthEvent.getBids(), depthEvent.getAsks(), initialSnapshot);
        tls.forEach(tl -> {
            tl.process(generalTradeList);
            eventDistributor.distribute(tl.getSettings().getSettingsHash(), tl.getMaxLevel(), tl.toDTO());
        });
    }

    public synchronized void addTL(TradeList tradeList) {
        tls.add(tradeList);
    }

    public synchronized void removeTL(TradeList tradeList) {
        tls.remove(tradeList);
    }
}