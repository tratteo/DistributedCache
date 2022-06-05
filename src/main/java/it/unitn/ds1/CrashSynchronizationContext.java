package it.unitn.ds1;

import java.util.concurrent.Semaphore;

public class CrashSynchronizationContext {
    public final Semaphore crashSemaphore;
    private int currentCrashes = 0;

    public CrashSynchronizationContext() {
        crashSemaphore = new Semaphore(1, true);
    }

    public synchronized boolean canCrash(Configuration.ProtocolStage stage) throws InterruptedException {
        crashSemaphore.acquire();
        boolean res = Configuration.STAGES_CRASH.contains(stage) && currentCrashes < Configuration.MAX_CONCURRENT_CRASHES;
        crashSemaphore.release();
        return res;
    }

    public synchronized void incrementCrashes() throws InterruptedException {
        crashSemaphore.acquire();
        currentCrashes++;
        crashSemaphore.release();
    }

    public synchronized void decrementCrashes() throws InterruptedException {
        crashSemaphore.acquire();
        currentCrashes--;
        currentCrashes = Math.min(currentCrashes, 0);
        crashSemaphore.release();
    }
}
