package zonely.ams.adcore.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import java.util.concurrent.atomic.AtomicBoolean;

import zonely.ams.adcore.export.AuditManager;
import zonely.ams.adcore.export.ExportManager;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.sync.SyncManager;
import zonely.ams.adcore.sync.SyncResult;
import zonely.ams.adcore.util.AppExecutors;

public class AdcoreSyncService extends Service {
    public static final String ACTION_INITIAL_SYNC = "zonely.ams.adcore.action.INITIAL_SYNC";
    public static final String ACTION_DAILY_PULL = "zonely.ams.adcore.action.DAILY_PULL";
    public static final String EXTRA_EMIT_PROGRESS = "emit_progress";
    private static final String TAG = "AdcoreSyncService";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    public static void startInitial(Context context, boolean emitProgress) {
        Intent intent = new Intent(context, AdcoreSyncService.class);
        intent.setAction(ACTION_INITIAL_SYNC);
        intent.putExtra(EXTRA_EMIT_PROGRESS, emitProgress);
        context.startService(intent);
    }

    public static void startDaily(Context context, boolean emitProgress) {
        Intent intent = new Intent(context, AdcoreSyncService.class);
        intent.setAction(ACTION_DAILY_PULL);
        intent.putExtra(EXTRA_EMIT_PROGRESS, emitProgress);
        context.startService(intent);
    }

    public static boolean isRunning() {
        return RUNNING.get();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (!RUNNING.compareAndSet(false, true)) {
            AdcoreLogger.i(TAG, "Sync request ignored because a sync is already running. action=" + intent.getAction());
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    boolean emit = intent.getBooleanExtra(EXTRA_EMIT_PROGRESS, false);
                    SyncManager manager = new SyncManager(AdcoreSyncService.this);
                    SyncResult result;
                    if (ACTION_DAILY_PULL.equals(intent.getAction())) {
                        result = manager.runDailyPull(emit);
                    } else {
                        result = manager.runInitialSync(emit);
                    }
                    if (result.success) {
                        afterSuccessfulSync();
                    }
                } catch (Exception exception) {
                    AdcoreLogger.e(TAG, "Sync service crashed.", exception);
                } finally {
                    RUNNING.set(false);
                    stopSelf(startId);
                }
            }
        });
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void afterSuccessfulSync() {
        new AuditManager(this).sendPreviousDayOnce();
        try {
            ExportManager exportManager = new ExportManager(this);
            exportManager.prepareDatabaseExport();
            exportManager.prepareLogArchiveExport();
        } catch (Exception exception) {
            AdcoreLogger.e(TAG, "Post-sync export preparation failed.", exception);
        }
    }
}
