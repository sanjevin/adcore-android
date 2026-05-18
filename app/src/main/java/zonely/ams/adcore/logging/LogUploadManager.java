package zonely.ams.adcore.logging;

import android.content.Context;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;

import zonely.ams.adcore.api.ApiClient;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.model.RetryConfig;
import zonely.ams.adcore.util.DeviceIdProvider;
import zonely.ams.adcore.util.FileUtils;
import zonely.ams.adcore.util.TimeUtils;

public class LogUploadManager {
    private static final String TAG = "LogUploadManager";
    private final Context appContext;
    private final AdcoreDatabase db;

    public LogUploadManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AdcoreDatabase.getInstance(appContext);
    }

    public void scanAndUploadErrors() {
        try {
            List<File> errorFiles = new ArrayList<>();
            for (File file : AdcoreLogger.getLogFilesNewestFirst(appContext)) {
                if (containsError(file)) {
                    errorFiles.add(file);
                }
            }
            if (errorFiles.isEmpty()) {
                AdcoreLogger.i(TAG, "Hourly log scan completed. No ERROR logs found.");
                return;
            }
            String signature = signature(errorFiles);
            if (db.wasErrorSignatureSent(signature)) {
                AdcoreLogger.i(TAG, "Error log signature already uploaded. signature=" + signature);
                return;
            }
            File zip = new File(FileUtils.exportsDir(appContext), "error-logs-" + TimeUtils.now() + ".zip");
            FileUtils.zipFiles(errorFiles, zip);
            uploadWithRetry(zip, signature);
        } catch (Exception exception) {
            AdcoreLogger.e(TAG, "Hourly error log upload failed.", exception);
        }
    }

    private void uploadWithRetry(File zip, String signature) throws Exception {
        RetryConfig retry = db.getBackgroundRetryConfig();
        Exception last = null;
        for (int attempt = 0; attempt <= retry.maxRetries; attempt++) {
            try {
                new ApiClient(appContext).sendDeviceLogs(zip, DeviceIdProvider.getDeviceId(appContext), "ERROR");
                db.markErrorSignatureSent(signature);
                AdcoreLogger.i(TAG, "Error logs uploaded. signature=" + signature + " attempts=" + (attempt + 1));
                return;
            } catch (Exception exception) {
                last = exception;
                AdcoreLogger.w(TAG, "Error log upload attempt failed. attempt=" + (attempt + 1), exception);
                if (attempt < retry.maxRetries) {
                    sleep(retry.delaySeconds);
                }
            }
        }
        throw last == null ? new IllegalStateException("Error log upload failed") : last;
    }

    private boolean containsError(File file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains(" | ERROR | ")) {
                    return true;
                }
            }
        }
        return false;
    }

    private String signature(List<File> files) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        for (File file : files) {
            String data = file.getName() + ":" + file.length() + ":" + file.lastModified() + "\n";
            digest.update(data.getBytes("UTF-8"));
        }
        byte[] hash = digest.digest();
        StringBuilder builder = new StringBuilder();
        for (byte b : hash) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
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
