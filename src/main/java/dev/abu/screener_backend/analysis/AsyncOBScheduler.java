package dev.abu.screener_backend.analysis;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class AsyncOBScheduler {

    private static final ThreadPoolExecutor spotExecService = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread thread = new Thread(r);
                thread.setName("spot-scheduler");
                return thread;
            });

    private static final ThreadPoolExecutor futExecService = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(),
            r -> {
                Thread thread = new Thread(r);
                thread.setName("fut-scheduler");
                return thread;
            });

    // it's possible that two objects may call this method at the same time with different isSpot values
    public synchronized static void scheduleTask(Runnable task, boolean isSpot) {
        if (isSpot) spotExecService.submit(task);
        else futExecService.submit(task);
    }

    public static long getNumOfScheduledTasks(boolean isSpot) {
        if (isSpot) return spotExecService.getQueue().size();
        return futExecService.getQueue().size();
    }
}
