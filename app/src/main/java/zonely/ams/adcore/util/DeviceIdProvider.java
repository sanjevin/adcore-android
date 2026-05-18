package zonely.ams.adcore.util;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

import java.lang.reflect.Method;
import java.util.UUID;

import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;

public final class DeviceIdProvider {
    private static final String TAG = "DeviceIdProvider";

    private DeviceIdProvider() {
    }

    public static String getDeviceId(Context context) {
        String serial = readDeviceSerial(context);
        if (isUsable(serial)) {
            return serial;
        }
        String androidId = Settings.Secure.getString(context.getContentResolver(), Settings.Secure.ANDROID_ID);
        if (isUsable(androidId)) {
            AdcoreLogger.w(TAG, "Falling back to ANDROID_ID because printed serial was unavailable.");
            return androidId;
        }
        AdcoreDatabase db = AdcoreDatabase.getInstance(context);
        String uuid = db.getMarker("generated_device_uuid");
        if (!isUsable(uuid)) {
            uuid = UUID.randomUUID().toString();
            db.setMarker("generated_device_uuid", uuid);
        }
        AdcoreLogger.w(TAG, "Falling back to generated UUID because no platform device id was available.");
        return uuid;
    }

    @SuppressLint({"HardwareIds", "MissingPermission"})
    private static String readDeviceSerial(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED) {
                    return Build.getSerial();
                }
                AdcoreLogger.w(TAG, "READ_PHONE_STATE is not granted; Build.getSerial is unavailable.");
            } else {
                return Build.SERIAL;
            }
        } catch (SecurityException securityException) {
            AdcoreLogger.w(TAG, "SecurityException while reading Build serial.", securityException);
        } catch (Exception exception) {
            AdcoreLogger.w(TAG, "Unexpected error while reading Build serial.", exception);
        }
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getMethod("get", String.class);
            Object value = get.invoke(null, "ro.serialno");
            return value == null ? null : String.valueOf(value);
        } catch (Exception exception) {
            AdcoreLogger.w(TAG, "Unable to read ro.serialno system property.", exception);
        }
        return null;
    }

    private static boolean isUsable(String value) {
        return value != null
                && value.trim().length() > 0
                && !"unknown".equalsIgnoreCase(value.trim())
                && !"9774d56d682e549c".equals(value.trim());
    }
}
