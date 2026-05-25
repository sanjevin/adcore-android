package zonely.ams.adcore.install;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

import zonely.ams.adcore.logging.AdcoreLogger;

public class AppInstaller {
    private static final String TAG = "AppInstaller";
    private final Context appContext;

    public AppInstaller(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public boolean isDeviceOwner() {
        DevicePolicyManager manager = (DevicePolicyManager) appContext.getSystemService(Context.DEVICE_POLICY_SERVICE);
        return manager != null && manager.isDeviceOwnerApp(appContext.getPackageName());
    }

    public void installSilently(File apkFile) throws Exception {
        validateApk(apkFile);
        PackageInstaller installer = appContext.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        params.setAppPackageName(appContext.getPackageName());
        int sessionId = installer.createSession(params);
        PackageInstaller.Session session = installer.openSession(sessionId);
        try {
            try (OutputStream output = session.openWrite("adcore-update", 0, apkFile.length());
                 FileInputStream input = new FileInputStream(apkFile)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                session.fsync(output);
            }
            Intent intent = new Intent(appContext, InstallResultReceiver.class);
            intent.setAction("zonely.ams.adcore.INSTALL_RESULT");
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags |= PendingIntent.FLAG_IMMUTABLE;
            }
            PendingIntent pendingIntent = PendingIntent.getBroadcast(appContext, sessionId, intent, flags);
            session.commit(pendingIntent.getIntentSender());
            AdcoreLogger.i(TAG, "Silent APK install committed. sessionId=" + sessionId + " apk=" + apkFile.getAbsolutePath());
        } finally {
            session.close();
        }
    }

    public void launchInstallerUi(Activity activity, File apkFile, int requestCode) {
        validateApk(apkFile);
        Uri uri = FileProvider.getUriForFile(appContext, appContext.getPackageName() + ".fileprovider", apkFile);
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        activity.startActivityForResult(intent, requestCode);
        AdcoreLogger.i(TAG, "Interactive installer UI launched from prompt activity. apk=" + apkFile.getAbsolutePath());
    }

    private void validateApk(File apkFile) {
        if (apkFile == null || !apkFile.exists() || apkFile.length() == 0L) {
            throw new IllegalArgumentException("APK file is missing or empty: "
                    + (apkFile == null ? null : apkFile.getAbsolutePath()));
        }
    }

    public ComponentName adminComponent() {
        return new ComponentName(appContext, AdcoreDeviceAdminReceiver.class);
    }
}
