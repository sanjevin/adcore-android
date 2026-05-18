package zonely.ams.adcore;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.scheduler.AdcoreScheduler;
import zonely.ams.adcore.session.SessionManager;
import zonely.ams.adcore.util.TimeUtils;

public class AdcoreApplication extends Application {
    private int visibleActivities;

    @Override
    public void onCreate() {
        super.onCreate();
        AdcoreLogger.init(this);
        final AdcoreDatabase db = AdcoreDatabase.getInstance(this);
        if (!db.hasOpenUptimeSession("DEVICE")) {
            db.startUptimeSession("DEVICE", TimeUtils.now());
        }
        SessionManager.loadStoredSession(this);
        AdcoreScheduler.scheduleAll(this);
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            }

            @Override
            public void onActivityStarted(Activity activity) {
                if (visibleActivities == 0) {
                    db.startUptimeSession("APP", TimeUtils.now());
                }
                visibleActivities++;
            }

            @Override
            public void onActivityResumed(Activity activity) {
            }

            @Override
            public void onActivityPaused(Activity activity) {
            }

            @Override
            public void onActivityStopped(Activity activity) {
                visibleActivities = Math.max(0, visibleActivities - 1);
                if (visibleActivities == 0) {
                    db.closeOpenUptimeSessions("APP", TimeUtils.now());
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
            }

            @Override
            public void onActivityDestroyed(Activity activity) {
            }
        });
        AdcoreLogger.i("AdcoreApplication", "Application initialized.");
    }
}
