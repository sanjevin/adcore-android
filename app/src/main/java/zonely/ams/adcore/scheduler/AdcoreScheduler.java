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
import zonely.ams.adcore.service.DeviceDataUploadJobService;
import zonely.ams.adcore.service.HourlyLogScanJobService;
import zonely.ams.adcore.util.TimeUtils;

public final class AdcoreScheduler {
    public static final int JOB_DAILY_SYNC = 1001;
    public static final int JOB_HOURLY_LOG_SCAN = 1002;
    public static final int JOB_APP_UPDATE = 1003;
    public static final int JOB_APP_UPDATE_NOW = 1004;
    public static final int JOB_DEVICE_DATA_UPLOAD = 1005;
    private static final String TAG = "AdcoreScheduler";

    private AdcoreScheduler() {
    }

    public static void scheduleAll(Context context) {
        scheduleResourceSyncAlarm(context);
        scheduleHourlyLogScan(context);
        scheduleDailyAppUpdate(context);
        scheduleNextDeviceDataUpload(context);
    }

    public static void scheduleResourceSyncAlarm(Context context) {
        Context appContext = context.getApplicationContext();
        scheduleResourceSyncAlarmAt(appContext, nextResourceSyncTriggerAt(appContext), "last-success");
    }

    public static void scheduleNextResourceSyncAlarm(Context context) {
        Context appContext = context.getApplicationContext();
        long triggerAt = TimeUtils.now() + resourceSyncIntervalMs(appContext);
        scheduleResourceSyncAlarmAt(appContext, triggerAt, "interval");
    }

    private static void scheduleResourceSyncAlarmAt(Context appContext, long triggerAt, String reason) {
        Intent intent = new Intent(appContext, DailySyncAlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(appContext, 3001, intent, pendingFlags());
        AlarmManager alarmManager = (AlarmManager) appContext.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            AdcoreLogger.e(TAG, "AlarmManager unavailable; resource metadata sync alarm not scheduled.");
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
            AdcoreLogger.i(TAG, "Resource metadata sync exact alarm scheduled for " + TimeUtils.isoUtc(triggerAt)
                    + " interval=" + resourceSyncIntervalLabel(appContext)
                    + " reason=" + reason);
        } catch (SecurityException exception) {
            AdcoreLogger.w(TAG, "Exact alarm permission unavailable; falling back to inexact resource sync alarm.",
                    exception);
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    public static void scheduleImmediateResourceSync(Context context) {
        JobInfo jobInfo = new JobInfo.Builder(JOB_DAILY_SYNC,
                new ComponentName(context.getApplicationContext(), DailySyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(0L)
                .setOverrideDeadline(1000L)
                .setBackoffCriteria(5 * 60 * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
                .build();
        schedule(context, jobInfo);
    }

    public static void ensureMissedResourceSync(Context context) {
        if (isResourceSyncDue(context)) {
            AdcoreLogger.i(TAG, "Resource metadata sync is due. Scheduling immediate background sync. interval="
                    + resourceSyncIntervalLabel(context));
            scheduleImmediateResourceSync(context);
        }
    }

    public static boolean isResourceSyncDue(Context context) {
        long lastSuccess = lastResourceSyncSuccessAt(context);
        if (lastSuccess <= 0L) {
            return true;
        }
        return TimeUtils.now() >= lastSuccess + resourceSyncIntervalMs(context);
    }

    public static String resourceSyncIntervalLabel(Context context) {
        return resourceSyncIntervalMinutes(context) + " minutes";
    }

    private static long nextResourceSyncTriggerAt(Context context) {
        long now = TimeUtils.now();
        long intervalMs = resourceSyncIntervalMs(context);
        long lastSuccess = lastResourceSyncSuccessAt(context);
        if (lastSuccess <= 0L) {
            return now + intervalMs;
        }
        long dueAt = lastSuccess + intervalMs;
        return dueAt <= now ? now + 1000L : dueAt;
    }

    private static long lastResourceSyncSuccessAt(Context context) {
        AdcoreDatabase db = AdcoreDatabase.getInstance(context);
        long lastSuccess = parseLong(db.getMarker(AppConstants.MARKER_LAST_RESOURCE_SYNC_SUCCESS));
        if (lastSuccess <= 0L) {
            lastSuccess = parseLong(db.getMarker(AppConstants.MARKER_LAST_DAILY_SYNC_SUCCESS));
        }
        return lastSuccess;
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

    public static void scheduleNextDeviceDataUpload(Context context) {
        scheduleDeviceDataUploadAfter(context, deviceDataUploadIntervalMs(context));
    }

    public static void scheduleImmediateDeviceDataUpload(Context context) {
        scheduleDeviceDataUploadAfter(context, 0L);
    }

    private static void scheduleDeviceDataUploadAfter(Context context, long delayMs) {
        long safeDelay = Math.max(0L, delayMs);
        long deadline = safeDelay == 0L ? 1000L : safeDelay + 5L * 60L * 1000L;
        JobInfo jobInfo = new JobInfo.Builder(JOB_DEVICE_DATA_UPLOAD,
                new ComponentName(context, DeviceDataUploadJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(safeDelay)
                .setOverrideDeadline(deadline)
                .setBackoffCriteria(5L * 60L * 1000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .setPersisted(true)
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

    private static long resourceSyncIntervalMs(Context context) {
        return resourceSyncIntervalMinutes(context) * 60L * 1000L;
    }

    private static long deviceDataUploadIntervalMs(Context context) {
        return deviceDataUploadIntervalMinutes(context) * 60L * 1000L;
    }

    private static int resourceSyncIntervalMinutes(Context context) {
        int value = AdcoreDatabase.getInstance(context).getConfigInt(
                AppConstants.CONFIG_RESOURCE_SYNC_INTERVAL_MINUTES,
                AppConstants.DEFAULT_RESOURCE_SYNC_INTERVAL_MINUTES);
        if (value <= 0) {
            AdcoreLogger.w(TAG, "Invalid resource sync interval config: " + value
                    + ". Falling back to " + AppConstants.DEFAULT_RESOURCE_SYNC_INTERVAL_MINUTES + " minutes.");
            return AppConstants.DEFAULT_RESOURCE_SYNC_INTERVAL_MINUTES;
        }
        return value;
    }

    private static int deviceDataUploadIntervalMinutes(Context context) {
        int value = AdcoreDatabase.getInstance(context).getConfigInt(
                AppConstants.CONFIG_DEVICE_DATA_UPLOAD_INTERVAL_MINUTES,
                AppConstants.DEFAULT_DEVICE_DATA_UPLOAD_INTERVAL_MINUTES);
        if (value <= 0) {
            AdcoreLogger.w(TAG, "Invalid device data upload interval config: " + value
                    + ". Falling back to " + AppConstants.DEFAULT_DEVICE_DATA_UPLOAD_INTERVAL_MINUTES + " minutes.");
            return AppConstants.DEFAULT_DEVICE_DATA_UPLOAD_INTERVAL_MINUTES;
        }
        return value;
    }

    private static long parseLong(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            AdcoreLogger.w(TAG, "Invalid resource sync marker value: " + value, exception);
            return 0L;
        }
    }
}
