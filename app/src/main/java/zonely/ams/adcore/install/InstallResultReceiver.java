package zonely.ams.adcore.install;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import zonely.ams.adcore.logging.AdcoreLogger;

public class InstallResultReceiver extends BroadcastReceiver {
    private static final String TAG = "InstallResultReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_SUCCESS) {
            AdcoreLogger.i(TAG, "APK install committed successfully.");
        } else {
            AdcoreLogger.e(TAG, "APK install failed. status=" + status + " message=" + message);
        }
    }
}
