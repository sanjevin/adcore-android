package zonely.ams.adcore.sync;

import java.util.concurrent.atomic.AtomicBoolean;

public final class SyncExecutionGate {
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private SyncExecutionGate() {
    }

    public static boolean tryAcquire() {
        return RUNNING.compareAndSet(false, true);
    }

    public static void release() {
        RUNNING.set(false);
    }

    public static boolean isRunning() {
        return RUNNING.get();
    }
}
