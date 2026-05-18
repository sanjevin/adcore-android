package zonely.ams.adcore.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import zonely.ams.adcore.logging.AdcoreLogger;

public class DailySyncAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "DailySyncAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        AdcoreLogger.i(TAG, "Daily 00:30 alarm received.");
        AdcoreScheduler.scheduleImmediateDailySync(context);
        AdcoreScheduler.scheduleDailySyncAlarm(context);
    }
}
