package zonely.ams.adcore.export;

import android.content.Context;

import org.json.JSONObject;

import java.util.Map;

import zonely.ams.adcore.AdcoreContext;
import zonely.ams.adcore.BuildConfig;
import zonely.ams.adcore.api.ApiClient;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.model.RetryConfig;
import zonely.ams.adcore.util.DeviceIdProvider;
import zonely.ams.adcore.util.TimeUtils;

public class AuditManager {
    private static final String TAG = "AuditManager";
    private final Context appContext;
    private final AdcoreDatabase db;

    public AuditManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AdcoreDatabase.getInstance(appContext);
    }

    public void sendPreviousDayOnce() {
        String dateKey = TimeUtils.yesterdayKey();
        String marker = db.getMarker("audit_sent_" + dateKey);
        if ("true".equals(marker)) {
            AdcoreLogger.i(TAG, "Previous day audit already sent. date=" + dateKey);
            return;
        }
        try {
            JSONObject payload = buildPayload(dateKey);
            sendWithRetry(payload);
            db.setMarker("audit_sent_" + dateKey, "true");
            db.setMarker("last_audit_sent_date", dateKey);
            AdcoreLogger.i(TAG, "Previous day audit sent. date=" + dateKey);
        } catch (Exception exception) {
            AdcoreLogger.e(TAG, "Previous day audit failed. date=" + dateKey, exception);
        }
    }

    private JSONObject buildPayload(String dateKey) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("userId", AdcoreContext.getUserId());
        payload.put("deviceId", DeviceIdProvider.getDeviceId(appContext));
        payload.put("appDataDate", TimeUtils.isoUtc(TimeUtils.startOfLocalDay(dateKey)));
        payload.put("deviceStartInstant", TimeUtils.isoUtc(TimeUtils.startOfLocalDay(dateKey)));
        payload.put("deviceEndInstant", TimeUtils.isoUtc(TimeUtils.endOfLocalDayExclusive(dateKey) - 1));
        payload.put("appStartInstant", TimeUtils.isoUtc(TimeUtils.startOfLocalDay(dateKey)));
        payload.put("appEndInstant", TimeUtils.isoUtc(TimeUtils.endOfLocalDayExclusive(dateKey) - 1));
        payload.put("totalPlayTimeInSec", db.getTotalPlaySeconds(dateKey));
        payload.put("totalSyncTimeInSec", db.getTotalSuccessfulSyncSeconds(dateKey));
        payload.put("totalAppUpTimeInSec", db.getTotalUptimeSeconds("APP", dateKey));
        payload.put("totalDeviceUpTimeInSec", db.getTotalUptimeSeconds("DEVICE", dateKey));
        payload.put("appVersion", BuildConfig.VERSION_NAME);
        JSONObject playCounts = new JSONObject();
        for (Map.Entry<String, Integer> entry : db.getPlaybackCountMap(dateKey).entrySet()) {
            playCounts.put(entry.getKey(), entry.getValue());
        }
        payload.put("resourceIdPlayCountMap", playCounts);
        return payload;
    }

    private void sendWithRetry(JSONObject payload) throws Exception {
        RetryConfig retry = db.getBackgroundRetryConfig();
        Exception last = null;
        for (int attempt = 0; attempt <= retry.maxRetries; attempt++) {
            try {
                new ApiClient(appContext).sendDeviceData(DeviceIdProvider.getDeviceId(appContext), payload);
                return;
            } catch (Exception exception) {
                last = exception;
                AdcoreLogger.w(TAG, "sendDeviceData attempt failed. attempt=" + (attempt + 1), exception);
                if (attempt < retry.maxRetries) {
                    sleep(retry.delaySeconds);
                }
            }
        }
        throw last == null ? new IllegalStateException("sendDeviceData failed") : last;
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
