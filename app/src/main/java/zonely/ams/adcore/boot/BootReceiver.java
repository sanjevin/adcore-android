package zonely.ams.adcore.boot;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import zonely.ams.adcore.activity.SplashActivity;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.scheduler.AdcoreScheduler;
import zonely.ams.adcore.util.TimeUtils;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        AdcoreLogger.i(TAG, "Boot receiver invoked. action=" + action);
        AdcoreDatabase.getInstance(context).startUptimeSession("DEVICE", TimeUtils.now());
        AdcoreScheduler.scheduleAll(context);
        Intent launch = new Intent(context, SplashActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        context.startActivity(launch);
    }
}
