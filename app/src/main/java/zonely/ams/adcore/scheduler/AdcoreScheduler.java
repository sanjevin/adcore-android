package zonely.ams.adcore.scheduler;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import zonely.ams.adcore.config.AppConstants;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.service.AppUpdateJobService;
import zonely.ams.adcore.service.DailySyncJobService;
import zonely.ams.adcore.service.HourlyLogScanJobService;
import zonely.ams.adcore.util.TimeUtils;

public final class AdcoreScheduler {
    public static final int JOB_DAILY_SYNC = 1001;
    public static final int JOB_HOURLY_LOG_SCAN = 1002;
    public static final int JOB_APP_UPDATE = 1003;
    public static final int JOB_APP_UPDATE_NOW = 1004;
    private static final String TAG = "AdcoreScheduler";

    private AdcoreScheduler() {
    }

    public static void scheduleAll(Context context) {
        scheduleDailySyncAlarm(context);
        scheduleHourlyLogScan(context);
        scheduleDailyAppUpdate(context);
    }

    public static void scheduleDailySyncAlarm(Context context) {
        Context appContext = context.getApplicationContext();
        long triggerAt = TimeUtils.next0030();
        Intent intent = new Intent(appContext, DailySyncAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(appContext, 3001, intent, pendingFlags());
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            AdcoreLogger.e(TAG, "AlarmManager unavailable; daily sync alarm not scheduled.");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
            AdcoreLogger.i(TAG, "Daily sync exact alarm scheduled for " + TimeUtils.isoUtc(triggerAt));
        } catch (SecurityException exception) {
            AdcoreLogger.w(TAG, "Exact alarm permission unavailable; falling back to inexact daily sync alarm.", exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static void scheduleImmediateDailySync(Context context) {
        JobInfo jobInfo = new JobInfo.Builder(JOB_DAILY_SYNC,
                new ComponentName(context, DailySyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(0L)
                .setOverrideDeadline(1000L)
                .setBackoffCriteria(5 * 60 * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        schedule(context, jobInfo);
    }

    public static void ensureMissedDailySync(Context context) {
        if (isDailySyncDue(context)) {
            AdcoreLogger.i(TAG, "Missed 00:30 daily sync detected. Scheduling immediate daily sync.");
            scheduleImmediateDailySync(context);
        }
    }

    public static boolean isDailySyncDue(Context context) {
        AdcoreDatabase db = AdcoreDatabase.getInstance(context);
        long today0030 = TimeUtils.todayAt0030();
        long now = TimeUtils.now();
        String lastValue = db.getMarker(AppConstants.MARKER_LAST_DAILY_SYNC_SUCCESS);
        long lastSuccess = 0L;
        if (lastValue != null) {
            try {
                lastSuccess = Long.parseLong(lastValue);
            } catch (NumberFormatException ignored) {
                lastSuccess = 0L;
            }
        }
        return now > today0030 && lastSuccess < today0030;
    }

    public static void scheduleHourlyLogScan(Context context) {
        JobInfo jobInfo = new JobInfo.Builder(JOB_HOURLY_LOG_SCAN,
                new ComponentName(context, HourlyLogScanJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(60L * 60L * 1000L)
                .setPersisted(true)
                .build();
        schedule(context, jobInfo);
    }

    public static void scheduleDailyAppUpdate(Context context) {
        JobInfo.Builder builder = new JobInfo.Builder(JOB_APP_UPDATE,
                new ComponentName(context, AppUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setPeriodic(24L * 60L * 60L * 1000L, 60L * 60L * 1000L);
        } else {
            builder.setPeriodic(24L * 60L * 60L * 1000L);
        }
        schedule(context, builder.build());
    }

    public static void scheduleImmediateAppUpdate(Context context) {
        JobInfo jobInfo = new JobInfo.Builder(JOB_APP_UPDATE_NOW,
                new ComponentName(context, AppUpdateJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(0L)
                .setOverrideDeadline(1000L)
                .build();
        schedule(context, jobInfo);
    }

    private static void schedule(Context context, JobInfo jobInfo) {
        JobScheduler scheduler = (JobScheduler) context.getApplicationContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) {
            AdcoreLogger.e(TAG, "JobScheduler unavailable for jobId=" + jobInfo.getId());
            return;
        }
        int result = scheduler.schedule(jobInfo);
        AdcoreLogger.i(TAG, "Job scheduled. jobId=" + jobInfo.getId() + " result=" + result);
    }

    private static int pendingFlags() {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= PendingIntent.FLAG_IMMUTABLE;
        }
        return flags;
    }
}
