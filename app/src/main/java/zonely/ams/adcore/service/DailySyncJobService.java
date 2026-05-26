package zonely.ams.adcore.service;

import android.app.job.JobParameters;
import android.app.job.JobService;

import zonely.ams.adcore.export.ExportManager;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.sync.SyncResult;
import zonely.ams.adcore.sync.SyncManager;
import zonely.ams.adcore.util.AppExecutors;

public class DailySyncJobService extends JobService {
    private static final String TAG = "DailySyncJobService";

    @Override
    public boolean onStartJob(final JobParameters params) {
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                boolean retry = false;
                try {
                    SyncResult result = new SyncManager(DailySyncJobService.this).runDailyPull(false);
                    retry = !result.success;
                    if (result.success) {
                        ExportManager exportManager = new ExportManager(DailySyncJobService.this);
                        exportManager.prepareDatabaseExport();
                        exportManager.prepareLogArchiveExport();
                    }
                } catch (Exception exception) {
                    retry = true;
                    AdcoreLogger.e(TAG, "Daily sync job failed.", exception);
                } finally {
                    jobFinished(params, retry);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        AdcoreLogger.w(TAG, "Daily sync job stopped by system; requesting retry.");
        return true;
    }
}
