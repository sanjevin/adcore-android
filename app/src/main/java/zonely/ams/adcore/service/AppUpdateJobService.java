package zonely.ams.adcore.service;

import android.app.job.JobParameters;
import android.app.job.JobService;

import zonely.ams.adcore.install.AppUpdateManager;
import zonely.ams.adcore.util.AppExecutors;

public class AppUpdateJobService extends JobService {
    @Override
    public boolean onStartJob(final JobParameters params) {
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                new AppUpdateManager(AppUpdateJobService.this).checkOncePerDay();
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
