package zonely.ams.adcore.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.util.TimeUtils;

public class ShutdownReceiver extends BroadcastReceiver {
    private static final String TAG = "ShutdownReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        long now = TimeUtils.now();
        AdcoreDatabase db = AdcoreDatabase.getInstance(context);
        db.closeOpenUptimeSessions("DEVICE", now);
        db.closeOpenUptimeSessions("APP", now);
        AdcoreLogger.i(TAG, "Shutdown received; uptime sessions closed.");
    }
}
