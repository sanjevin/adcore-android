package zonely.ams.adcore.export;

import android.content.Context;

import org.json.JSONObject;

import java.util.List;
import java.util.Map;

import zonely.ams.adcore.AdcoreContext;
import zonely.ams.adcore.BuildConfig;
import zonely.ams.adcore.api.ApiClient;
import zonely.ams.adcore.config.AppConstants;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.model.DeviceDataDelta;
import zonely.ams.adcore.model.RetryConfig;
import zonely.ams.adcore.util.DeviceIdProvider;
import zonely.ams.adcore.util.TimeUtils;

public class DeviceDataUploadManager {
    private static final String TAG = "DeviceDataUploadManager";
    private final Context appContext;
    private final AdcoreDatabase db;

    public DeviceDataUploadManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AdcoreDatabase.getInstance(appContext);
    }

    public int uploadPendingDeviceData() throws Exception {
        List<DeviceDataDelta> deltas = db.getPendingDeviceDataDeltas(TimeUtils.now());
        if (deltas.isEmpty()) {
            AdcoreLogger.i(TAG, "No pending device data deltas to upload.");
            db.setMarker(AppConstants.MARKER_LAST_DEVICE_DATA_UPLOAD_AT, String.valueOf(TimeUtils.now()));
            return 0;
        }

        ApiClient apiClient = new ApiClient(appContext);
        int uploaded = 0;
        for (DeviceDataDelta delta : deltas) {
            JSONObject payload = buildPayload(delta);
            boolean sent = sendWithRetry(apiClient, payload);
            if (!sent) {
                return uploaded;
            }
            db.markDeviceDataDeltaUploaded(delta);
            uploaded++;
            AdcoreLogger.i(TAG, "Device data delta uploaded. date=" + delta.dateKey
                    + " playSeconds=" + delta.totalPlayTimeInSec
                    + " syncSeconds=" + delta.totalSyncTimeInSec
                    + " appUptimeSeconds=" + delta.totalAppUpTimeInSec
                    + " deviceUptimeSeconds=" + delta.totalDeviceUpTimeInSec
                    + " resourceCount=" + delta.resourceIdPlayCountMap.size());
        }
        db.setMarker(AppConstants.MARKER_LAST_DEVICE_DATA_UPLOAD_AT, String.valueOf(TimeUtils.now()));
        return uploaded;
    }

    private JSONObject buildPayload(DeviceDataDelta delta) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("userId", AdcoreContext.getUserId());
        payload.put("deviceId", DeviceIdProvider.getDeviceId(appContext));
        payload.put("appDataDate", delta.dateKey);
        payload.put("deviceStartInstant", TimeUtils.isoUtc(delta.windowStartMs));
        payload.put("deviceEndInstant", TimeUtils.isoUtc(delta.windowEndMs));
        payload.put("appStartInstant", TimeUtils.isoUtc(delta.windowStartMs));
        payload.put("appEndInstant", TimeUtils.isoUtc(delta.windowEndMs));
        payload.put("totalPlayTimeInSec", delta.totalPlayTimeInSec);
        payload.put("totalSyncTimeInSec", delta.totalSyncTimeInSec);
        payload.put("totalAppUpTimeInSec", delta.totalAppUpTimeInSec);
        payload.put("totalDeviceUpTimeInSec", delta.totalDeviceUpTimeInSec);
        payload.put("appVersion", BuildConfig.VERSION_NAME);
        JSONObject playCounts = new JSONObject();
        for (Map.Entry<String, Integer> entry : delta.resourceIdPlayCountMap.entrySet()) {
            playCounts.put(entry.getKey(), entry.getValue());
        }
        payload.put("resourceIdPlayCountMap", playCounts);
        return payload;
    }

    private boolean sendWithRetry(ApiClient apiClient, JSONObject payload) throws Exception {
        RetryConfig retry = db.getBackgroundRetryConfig();
        Exception last = null;
        for (int attempt = 0; attempt <= retry.maxRetries; attempt++) {
            try {
                apiClient.sendDeviceData(DeviceIdProvider.getDeviceId(appContext), payload);
                return true;
            } catch (Exception exception) {
                last = exception;
                if (ApiClient.isConnectivityFailure(exception)) {
                    AdcoreLogger.i(TAG, "sendDeviceData skipped because internet/server is unavailable.");
                    return false;
                }
                AdcoreLogger.w(TAG, "sendDeviceData delta attempt failed. attempt=" + (attempt + 1), exception);
                if (attempt < retry.maxRetries) {
                    sleep(retry.delaySeconds);
                }
            }
        }
        throw last == null ? new IllegalStateException("sendDeviceData delta failed") : last;
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
