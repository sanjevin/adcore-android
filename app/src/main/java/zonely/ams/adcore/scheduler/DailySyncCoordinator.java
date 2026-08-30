package zonely.ams.adcore.scheduler;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.sync.SyncResult;

public final class DailySyncCoordinator {
    public static final String ACTION_RESOURCE_SYNC_COMPLETED = "zonely.ams.adcore.action.RESOURCE_SYNC_COMPLETED";
    private static final String TAG = "DailySyncCoordinator";
    private static final String PREFS_NAME = "resource_sync_state";
    private static final String KEY_ADS_REFRESH_PENDING = "ads_refresh_pending";
    private static final String KEY_COMPLETED_AT = "completed_at";
    private static final String KEY_MESSAGE = "message";

    private DailySyncCoordinator() {
    }

    public static void requestAdsRefreshAfterSync(Context context, SyncResult result) {
        Context appContext = context.getApplicationContext();
        prefs(appContext).edit()
                .putBoolean(KEY_ADS_REFRESH_PENDING, true)
                .putLong(KEY_COMPLETED_AT, System.currentTimeMillis())
                .putString(KEY_MESSAGE, result == null ? "" : result.message)
                .commit();
        Intent intent = new Intent(ACTION_RESOURCE_SYNC_COMPLETED);
        intent.setPackage(appContext.getPackageName());
        if (result != null) {
            intent.putExtra("totalResources", result.totalResources);
            intent.putExtra("downloadedResources", result.downloadedResources);
            intent.putExtra("message", result.message);
        }
        appContext.sendBroadcast(intent);
        AdcoreLogger.i(TAG, "Ads refresh requested after background resource sync. message="
                + (result == null ? null : result.message));
    }

    public static boolean isAdsRefreshPending(Context context) {
        return prefs(context).getBoolean(KEY_ADS_REFRESH_PENDING, false);
    }

    public static void clearAdsRefreshPending(Context context) {
        prefs(context).edit().clear().commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
