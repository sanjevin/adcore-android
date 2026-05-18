package zonely.ams.adcore.install;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;

import zonely.ams.adcore.logging.AdcoreLogger;

public class AdcoreDeviceAdminReceiver extends DeviceAdminReceiver {
    private static final String TAG = "DeviceAdmin";

    @Override
    public void onEnabled(Context context, Intent intent) {
        AdcoreLogger.i(TAG, "Device admin enabled.");
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        AdcoreLogger.w(TAG, "Device admin disabled.");
    }
}
