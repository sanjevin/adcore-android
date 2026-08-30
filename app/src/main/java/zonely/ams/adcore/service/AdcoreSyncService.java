package zonely.ams.adcore.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import zonely.ams.adcore.export.ExportManager;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.scheduler.AdcoreScheduler;
import zonely.ams.adcore.scheduler.DailySyncCoordinator;
import zonely.ams.adcore.sync.SyncExecutionGate;
import zonely.ams.adcore.sync.SyncManager;
import zonely.ams.adcore.sync.SyncResult;
import zonely.ams.adcore.util.AppExecutors;

public class AdcoreSyncService extends Service {
    public static final String ACTION_INITIAL_SYNC = "zonely.ams.adcore.action.INITIAL_SYNC";
    public static final String ACTION_DAILY_PULL = "zonely.ams.adcore.action.DAILY_PULL";
    public static final String EXTRA_EMIT_PROGRESS = "emit_progress";
    private static final String TAG = "AdcoreSyncService";

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
        return SyncExecutionGate.isRunning();
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (!SyncExecutionGate.tryAcquire()) {
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
                        AdcoreScheduler.scheduleResourceSyncAlarm(AdcoreSyncService.this);
                        if (ACTION_DAILY_PULL.equals(intent.getAction()) && !emit) {
                            DailySyncCoordinator.requestAdsRefreshAfterSync(AdcoreSyncService.this, result);
                        }
                    } else if (result.connectivityFailure && ACTION_DAILY_PULL.equals(intent.getAction())) {
                        AdcoreScheduler.scheduleNextResourceSyncAlarm(AdcoreSyncService.this);
                    }
                } catch (Exception exception) {
                    AdcoreLogger.e(TAG, "Sync service crashed.", exception);
                } finally {
                    SyncExecutionGate.release();
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
        try {
            ExportManager exportManager = new ExportManager(this);
            exportManager.prepareDatabaseExport();
            exportManager.prepareLogArchiveExport();
        } catch (Exception exception) {
            AdcoreLogger.e(TAG, "Post-sync export preparation failed.", exception);
        }
    }
}
