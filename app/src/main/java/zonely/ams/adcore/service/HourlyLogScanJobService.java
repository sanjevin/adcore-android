package zonely.ams.adcore.service;

import android.app.job.JobParameters;
import android.app.job.JobService;

import zonely.ams.adcore.logging.LogUploadManager;
import zonely.ams.adcore.util.AppExecutors;

public class HourlyLogScanJobService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                new LogUploadManager(HourlyLogScanJobService.this).scanAndUploadErrors();
                jobFinished(params, false);
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
