package zonely.ams.adcore.export;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import zonely.ams.adcore.api.ApiClient;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.util.DeviceIdProvider;
import zonely.ams.adcore.util.FileUtils;
import zonely.ams.adcore.util.TimeUtils;

public class ExportManager {
    private static final String TAG = "ExportManager";
    private final Context appContext;
    private final AdcoreDatabase db;

    public ExportManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AdcoreDatabase.getInstance(appContext);
    }

    public File prepareDatabaseExport() throws Exception {
        cleanupExports("db-export-");
        checkpointDatabase();
        List<File> files = new ArrayList<>();
        File dbFile = db.databaseFile();
        files.add(dbFile);
        File wal = new File(dbFile.getAbsolutePath() + "-wal");
        File shm = new File(dbFile.getAbsolutePath() + "-shm");
        if (wal.exists()) {
            files.add(wal);
        }
        if (shm.exists()) {
            files.add(shm);
        }
        File output = new File(FileUtils.exportsDir(appContext), "db-export-" + TimeUtils.now() + ".zip");
        FileUtils.zipFiles(files, output);
        AdcoreLogger.i(TAG, "Database export prepared. path=" + output.getAbsolutePath() + " size=" + output.length());
        return output;
    }

    public File prepareLogArchiveExport() throws Exception {
        cleanupExports("logs-export-");
        File output = new File(FileUtils.exportsDir(appContext), "logs-export-" + TimeUtils.now() + ".zip");
        FileUtils.zipFiles(AdcoreLogger.getLogFilesNewestFirst(appContext), output);
        AdcoreLogger.i(TAG, "Log archive export prepared. path=" + output.getAbsolutePath() + " size=" + output.length());
        return output;
    }

    public void uploadPreparedDatabase(File zipFile) throws Exception {
        new ApiClient(appContext).sendDeviceDb(zipFile, DeviceIdProvider.getDeviceId(appContext));
        AdcoreLogger.i(TAG, "Prepared database export uploaded. path=" + zipFile.getAbsolutePath());
    }

    public void uploadPreparedLogs(File zipFile) throws Exception {
        new ApiClient(appContext).sendDeviceLogs(zipFile, DeviceIdProvider.getDeviceId(appContext), "ALL");
        AdcoreLogger.i(TAG, "Prepared log archive uploaded. path=" + zipFile.getAbsolutePath());
    }

    private void cleanupExports(String prefix) {
        File dir = FileUtils.exportsDir(appContext);
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.getName().startsWith(prefix)) {
                FileUtils.deleteQuietly(file);
            }
        }
    }

    private void checkpointDatabase() {
        SQLiteDatabase writable = db.getWritableDatabase();
        writable.rawQuery("PRAGMA wal_checkpoint(FULL)", null).close();
    }
}
