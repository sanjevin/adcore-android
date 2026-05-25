package zonely.ams.adcore.install;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

import zonely.ams.adcore.activity.InstallPromptActivity;
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
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) {
            InstallState.clear(context);
        }
        Intent result = new Intent(InstallPromptActivity.ACTION_INSTALL_RESULT);
        result.setPackage(context.getPackageName());
        result.putExtra(InstallPromptActivity.EXTRA_INSTALL_STATUS, status);
        result.putExtra(InstallPromptActivity.EXTRA_INSTALL_MESSAGE, message);
        context.sendBroadcast(result);
    }
}
