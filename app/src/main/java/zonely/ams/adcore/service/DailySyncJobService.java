package zonely.ams.adcore.service;

import android.app.job.JobParameters;
import android.app.job.JobService;

import zonely.ams.adcore.export.ExportManager;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.scheduler.AdcoreScheduler;
import zonely.ams.adcore.scheduler.DailySyncCoordinator;
import zonely.ams.adcore.sync.SyncExecutionGate;
import zonely.ams.adcore.sync.SyncResult;
import zonely.ams.adcore.sync.SyncManager;
import zonely.ams.adcore.util.AppExecutors;

public class DailySyncJobService extends JobService {
    private static final String TAG = "DailySyncJobService";

    @Override
    public boolean onStartJob(final JobParameters params) {
        if (!AdcoreScheduler.isResourceSyncDue(this)) {
            AdcoreLogger.i(TAG, "Resource metadata sync job skipped because interval is not due.");
            AdcoreScheduler.scheduleResourceSyncAlarm(this);
            return false;
        }
        if (!SyncExecutionGate.tryAcquire()) {
            AdcoreLogger.i(TAG, "Resource metadata sync job skipped because another sync is running.");
            return false;
        }
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                boolean retry = false;
                try {
                    SyncResult result = new SyncManager(DailySyncJobService.this).runDailyPull(false);
                    retry = !result.success && !result.connectivityFailure;
                    if (result.success) {
                        ExportManager exportManager = new ExportManager(DailySyncJobService.this);
                        exportManager.prepareDatabaseExport();
                        exportManager.prepareLogArchiveExport();
                        DailySyncCoordinator.requestAdsRefreshAfterSync(DailySyncJobService.this, result);
                        AdcoreScheduler.scheduleResourceSyncAlarm(DailySyncJobService.this);
                    } else if (result.connectivityFailure) {
                        AdcoreScheduler.scheduleNextResourceSyncAlarm(DailySyncJobService.this);
                    }
                } catch (Exception exception) {
                    retry = true;
                    AdcoreLogger.e(TAG, "Resource metadata sync job failed.", exception);
                } finally {
                    SyncExecutionGate.release();
                    jobFinished(params, retry);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        SyncExecutionGate.release();
        AdcoreLogger.w(TAG, "Resource metadata sync job stopped by system; requesting retry.");
        return true;
    }
}
