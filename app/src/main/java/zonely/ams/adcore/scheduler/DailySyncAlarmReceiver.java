package zonely.ams.adcore.scheduler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import zonely.ams.adcore.activity.AdsActivity;
import zonely.ams.adcore.logging.AdcoreLogger;

public class DailySyncAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "DailySyncAlarmReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        AdcoreLogger.i(TAG, "Daily sync alarm received. localTime="
                + AdcoreScheduler.dailySyncTimeLabel(context));
        DailySyncCoordinator.requestForegroundSync(context);
        if (!AdsActivity.isActive()) {
            boolean opened = DailySyncCoordinator.openForegroundSync(context);
            if (!opened) {
                AdcoreLogger.w(TAG, "Foreground daily sync could not be opened; scheduling background retry job.");
                AdcoreScheduler.scheduleImmediateDailySync(context);
            }
        }
        AdcoreScheduler.scheduleDailySyncAlarm(context);
    }
}
