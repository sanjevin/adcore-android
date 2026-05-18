package zonely.ams.adcore.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import java.io.File;

import zonely.ams.adcore.export.ExportManager;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.util.AppExecutors;

public class LogArchiveExportService extends Service {
    public static final String ACTION_EXPORT = "zonely.ams.adcore.action.EXPORT_LOGS";
    public static final String ACTION_UPLOAD = "zonely.ams.adcore.action.UPLOAD_LOGS";
    private static final String TAG = "LogArchiveExportService";

    public static void startExport(Context context) {
        Intent intent = new Intent(context, LogArchiveExportService.class);
        intent.setAction(ACTION_EXPORT);
        context.startService(intent);
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, final int startId) {
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    ExportManager manager = new ExportManager(LogArchiveExportService.this);
                    File zip = manager.prepareLogArchiveExport();
                    if (intent != null && ACTION_UPLOAD.equals(intent.getAction())) {
                        manager.uploadPreparedLogs(zip);
                    }
                } catch (Exception exception) {
                    AdcoreLogger.e(TAG, "Log archive export service failed.", exception);
                } finally {
                    stopSelf(startId);
                }
            }
        });
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
