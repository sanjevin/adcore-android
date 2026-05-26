package zonely.ams.adcore.scheduler;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import zonely.ams.adcore.activity.SyncActivity;
import zonely.ams.adcore.logging.AdcoreLogger;

public final class DailySyncCoordinator {
    public static final String ACTION_DAILY_SYNC_DUE = "zonely.ams.adcore.action.DAILY_SYNC_DUE";
    private static final String TAG = "DailySyncCoordinator";
    private static final String PREFS_NAME = "daily_sync_state";
    private static final String KEY_PENDING = "pending";
    private static final String KEY_REQUESTED_AT = "requested_at";

    private DailySyncCoordinator() {
    }

    public static void requestForegroundSync(Context context) {
        Context appContext = context.getApplicationContext();
        prefs(appContext).edit()
                .putBoolean(KEY_PENDING, true)
                .putLong(KEY_REQUESTED_AT, System.currentTimeMillis())
                .commit();
        Intent intent = new Intent(ACTION_DAILY_SYNC_DUE);
        intent.setPackage(appContext.getPackageName());
        appContext.sendBroadcast(intent);
        AdcoreLogger.i(TAG, "Foreground daily sync requested.");
    }

    public static boolean isPending(Context context) {
        return prefs(context).getBoolean(KEY_PENDING, false);
    }

    public static void clearPending(Context context) {
        prefs(context).edit().clear().commit();
    }

    public static boolean openForegroundSync(Context context) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(appContext, SyncActivity.class);
        intent.putExtra(SyncActivity.EXTRA_FOREGROUND_DAILY_SYNC, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            appContext.startActivity(intent);
            AdcoreLogger.i(TAG, "Foreground daily sync activity opened.");
            return true;
        } catch (Exception exception) {
            AdcoreLogger.e(TAG, "Unable to open foreground daily sync activity.", exception);
            return false;
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
