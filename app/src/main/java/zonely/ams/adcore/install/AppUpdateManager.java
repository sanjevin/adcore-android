package zonely.ams.adcore.install;

import android.content.Context;

import java.io.File;

import zonely.ams.adcore.BuildConfig;
import zonely.ams.adcore.activity.InstallPromptActivity;
import zonely.ams.adcore.api.ApiClient;
import zonely.ams.adcore.config.AppConstants;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.model.RetryConfig;
import zonely.ams.adcore.util.FileUtils;
import zonely.ams.adcore.util.TimeUtils;

public class AppUpdateManager {
    private static final String TAG = "AppUpdateManager";
    private final Context appContext;
    private final AdcoreDatabase db;

    public AppUpdateManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AdcoreDatabase.getInstance(appContext);
    }

    public void checkOncePerDay() {
        String today = TimeUtils.todayKey();
        if (today.equals(db.getMarker(AppConstants.MARKER_LAST_UPDATE_CHECK_DATE))) {
            AdcoreLogger.i(TAG, "Latest-app check already completed today.");
            return;
        }
        try {
            boolean downloaded = downloadWithRetry();
            if (!downloaded) {
                db.setMarker(AppConstants.MARKER_LAST_UPDATE_CHECK_DATE, today);
                return;
            }
            File apk = new File(FileUtils.updatesDir(appContext), "adcore-latest.apk");
            boolean promptStarted = InstallPromptActivity.start(appContext, apk);
            if (promptStarted) {
                db.setMarker(AppConstants.MARKER_LAST_UPDATE_CHECK_DATE, today);
            }
            AdcoreLogger.i(TAG, "Latest APK install prompt requested. promptStarted=" + promptStarted);
        } catch (Exception exception) {
            AdcoreLogger.e(TAG, "Latest-app check failed.", exception);
        }
    }

    private boolean downloadWithRetry() throws Exception {
        RetryConfig retry = db.getBackgroundRetryConfig();
        File apk = new File(FileUtils.updatesDir(appContext), "adcore-latest.apk");
        Exception last = null;
        for (int attempt = 0; attempt <= retry.maxRetries; attempt++) {
            try {
                FileUtils.deleteQuietly(apk);
                boolean downloaded = new ApiClient(appContext).downloadLatestApp(BuildConfig.VERSION_NAME, apk, null);
                AdcoreLogger.i(TAG, "Latest-app check completed. downloaded=" + downloaded + " attempt=" + (attempt + 1));
                return downloaded;
            } catch (Exception exception) {
                last = exception;
                AdcoreLogger.w(TAG, "Latest-app check attempt failed. attempt=" + (attempt + 1), exception);
                if (attempt < retry.maxRetries) {
                    sleep(retry.delaySeconds);
                }
            }
        }
        throw last == null ? new IllegalStateException("Latest-app check failed") : last;
    }

    private void sleep(int seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }
}
