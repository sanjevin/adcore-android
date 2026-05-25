package zonely.ams.adcore.install;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;

public final class InstallState {
    private static final String PREFS_NAME = "adcore_install_state";
    private static final String KEY_INSTALL_PENDING = "install_pending";
    private static final String KEY_APK_PATH = "apk_path";

    private InstallState() {
    }

    public static void markPending(Context context, File apkFile) {
        prefs(context).edit()
                .putBoolean(KEY_INSTALL_PENDING, true)
                .putString(KEY_APK_PATH, apkFile.getAbsolutePath())
                .commit();
    }

    public static boolean isPending(Context context) {
        SharedPreferences preferences = prefs(context);
        if (!preferences.getBoolean(KEY_INSTALL_PENDING, false)) {
            return false;
        }
        File apkFile = getPendingApk(context);
        if (apkFile == null || !apkFile.exists() || apkFile.length() == 0L) {
            clear(context);
            return false;
        }
        return true;
    }

    public static File getPendingApk(Context context) {
        String path = prefs(context).getString(KEY_APK_PATH, null);
        if (path == null || path.trim().length() == 0) {
            return null;
        }
        return new File(path);
    }

    public static void clear(Context context) {
        prefs(context).edit().clear().commit();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
}
