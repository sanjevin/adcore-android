package zonely.ams.adcore.util;

import android.annotation.SuppressLint;
import android.content.Context;
import android.provider.Settings;

import zonely.ams.adcore.logging.AdcoreLogger;

public final class DeviceIdProvider {
    private static final String TAG = "DeviceIdProvider";

    private DeviceIdProvider() {
    }

    @SuppressLint("HardwareIds")
    public static String getDeviceId(Context context) {
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (!isUsableAndroidId(androidId)) {
            String message = "ANDROID_ID is unavailable or invalid; deviceId cannot be resolved.";
            AdcoreLogger.e(TAG, message + " value=" + androidId);
            throw new IllegalStateException(message);
        }
        return androidId.trim();
    }

    private static boolean isUsableAndroidId(String value) {
        return value != null
                && value.trim().length() > 0
                && !"unknown".equalsIgnoreCase(value.trim())
                && !"9774d56d682e549c".equalsIgnoreCase(value.trim());
    }

    /*
     * Previous fallback ideas kept here for reference only. They are intentionally inactive.
     *
     * Serial fallback:
     * - Build.getSerial() on Android O+ when READ_PHONE_STATE is granted.
     * - Build.SERIAL on pre-O devices.
     * - Reflection read of android.os.SystemProperties "ro.serialno".
     *
     * App-generated UUID fallback:
     * - Generate UUID.randomUUID().
     * - Store in SQLite markers.
     * - Use only when a future product decision accepts reset-on-clear-data behavior.
     */
}
