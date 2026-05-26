package zonely.ams.adcore.service;

import android.app.job.JobParameters;
import android.app.job.JobService;

import zonely.ams.adcore.export.DeviceDataUploadManager;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.scheduler.AdcoreScheduler;
import zonely.ams.adcore.util.AppExecutors;

public class DeviceDataUploadJobService extends JobService {
    private static final String TAG = "DeviceDataUploadJobService";

    @Override
    public boolean onStartJob(final JobParameters params) {
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                boolean retry = false;
                try {
                    int uploaded = new DeviceDataUploadManager(DeviceDataUploadJobService.this).uploadPendingDeviceData();
                    AdcoreLogger.i(TAG, "Device data upload job complete. uploadedBatches=" + uploaded);
                } catch (Exception exception) {
                    retry = true;
                    AdcoreLogger.e(TAG, "Device data upload job failed.", exception);
                } finally {
                    if (!retry) {
                        AdcoreScheduler.scheduleNextDeviceDataUpload(DeviceDataUploadJobService.this);
                    }
                    jobFinished(params, retry);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        AdcoreLogger.w(TAG, "Device data upload job stopped by system; requesting retry.");
        return true;
    }
}
