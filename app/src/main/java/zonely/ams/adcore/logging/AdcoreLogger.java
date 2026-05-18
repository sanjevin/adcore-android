package zonely.ams.adcore.logging;

import android.content.Context;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import zonely.ams.adcore.config.AppConstants;
import zonely.ams.adcore.util.FileUtils;

public final class AdcoreLogger {
    private static final Object LOCK = new Object();
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
    private static Context appContext;
    private static File currentFile;

    static {
        FORMAT.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    private AdcoreLogger() {
    }

    public static void init(Context context) {
        synchronized (LOCK) {
            appContext = context.getApplicationContext();
            try {
                FileUtils.logsDir(appContext);
                currentFile = selectWritableLogFile();
                i("AdcoreLogger", "Logger initialized. file=" + currentFile.getAbsolutePath());
            } catch (Exception exception) {
                Log.e("AdcoreLogger", "Logger initialization failed", exception);
            }
        }
    }

    public static void i(String tag, String message) {
        write("INFO", tag, message, null);
    }

    public static void w(String tag, String message) {
        write("WARN", tag, message, null);
    }

    public static void w(String tag, String message, Throwable throwable) {
        write("WARN", tag, message, throwable);
    }

    public static void e(String tag, String message) {
        write("ERROR", tag, message, null);
    }

    public static void e(String tag, String message, Throwable throwable) {
        write("ERROR", tag, message, throwable);
    }

    public static List<File> getLogFilesNewestFirst(Context context) {
        File[] files = FileUtils.logsDir(context.getApplicationContext()).listFiles();
        List<File> result = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".log")) {
                    result.add(file);
                }
            }
        }
        result.sort(new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(right.lastModified(), left.lastModified());
            }
        });
        return result;
    }

    private static void write(String level, String tag, String message, Throwable throwable) {
        String line = buildLine(level, tag, message, throwable);
        if ("ERROR".equals(level)) {
            Log.e(tag, message, throwable);
        } else if ("WARN".equals(level)) {
            Log.w(tag, message, throwable);
        } else {
            Log.i(tag, message);
        }
        synchronized (LOCK) {
            if (appContext == null) {
                return;
            }
            try {
                if (currentFile == null || currentFile.length() >= AppConstants.MAX_LOG_FILE_BYTES) {
                    currentFile = createNewLogFile();
                    trimOldLogs();
                }
                try (FileWriter writer = new FileWriter(currentFile, true)) {
                    writer.write(line);
                    writer.write('\n');
                }
            } catch (IOException exception) {
                Log.e("AdcoreLogger", "Unable to write log file", exception);
            }
        }
    }

    private static String buildLine(String level, String tag, String message, Throwable throwable) {
        StringBuilder builder = new StringBuilder(512);
        builder.append(FORMAT.format(new Date()));
        builder.append(" | ");
        builder.append(level);
        builder.append(" | pid=");
        builder.append(Process.myPid());
        builder.append(" | thread=");
        builder.append(Thread.currentThread().getName());
        builder.append(" | ");
        builder.append(tag == null ? "Adcore" : tag);
        builder.append(" | ");
        builder.append(message == null ? "" : message);
        if (throwable != null) {
            builder.append('\n');
            StringWriter stringWriter = new StringWriter();
            throwable.printStackTrace(new PrintWriter(stringWriter));
            builder.append(stringWriter);
        }
        return builder.toString();
    }

    private static File selectWritableLogFile() throws IOException {
        List<File> logs = getLogFilesNewestFirst(appContext);
        if (!logs.isEmpty() && logs.get(0).length() < AppConstants.MAX_LOG_FILE_BYTES) {
            return logs.get(0);
        }
        return createNewLogFile();
    }

    private static File createNewLogFile() throws IOException {
        File dir = FileUtils.logsDir(appContext);
        File file = new File(dir, "adcore-" + System.currentTimeMillis() + ".log");
        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Unable to create log file: " + file.getAbsolutePath());
        }
        trimOldLogs();
        return file;
    }

    private static void trimOldLogs() {
        File[] files = FileUtils.logsDir(appContext).listFiles();
        if (files == null || files.length <= AppConstants.MAX_LOG_FILES) {
            return;
        }
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File left, File right) {
                return Long.compare(right.lastModified(), left.lastModified());
            }
        });
        for (int i = AppConstants.MAX_LOG_FILES; i < files.length; i++) {
            File file = files[i];
            if (file.isFile() && file.getName().endsWith(".log") && !file.delete()) {
                Log.w("AdcoreLogger", "Unable to delete old log file: " + file.getAbsolutePath());
            }
        }
    }
}
