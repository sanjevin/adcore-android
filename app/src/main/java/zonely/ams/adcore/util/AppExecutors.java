package zonely.ams.adcore.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AppExecutors {
    private static final ExecutorService IO = Executors.newCachedThreadPool();
    private static final ExecutorService NETWORK = Executors.newFixedThreadPool(6);
    private static final ExecutorService SERIAL = Executors.newSingleThreadExecutor();

    private AppExecutors() {
    }

    public static ExecutorService io() {
        return IO;
    }

    public static ExecutorService network() {
        return NETWORK;
    }

    public static ExecutorService serial() {
        return SERIAL;
    }
}
