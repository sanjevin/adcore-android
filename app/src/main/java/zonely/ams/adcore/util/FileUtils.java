package zonely.ams.adcore.util;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import zonely.ams.adcore.config.AppConstants;

public final class FileUtils {
    private FileUtils() {
    }

    public static File privateDir(Context context, String child) {
        File dir = new File(context.getFilesDir(), child);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new IllegalStateException("Unable to create directory: " + dir.getAbsolutePath());
        }
        return dir;
    }

    public static File resourcesDir(Context context) {
        return privateDir(context, AppConstants.RESOURCE_DIR);
    }

    public static File exportsDir(Context context) {
        return privateDir(context, AppConstants.EXPORT_DIR);
    }

    public static File logsDir(Context context) {
        return privateDir(context, AppConstants.LOG_DIR);
    }

    public static File updatesDir(Context context) {
        return privateDir(context, AppConstants.UPDATE_DIR);
    }

    public static void deleteQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }

    public static String sanitizeFileName(String value) {
        if (value == null || value.trim().length() == 0) {
            return "resource";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static void zipFiles(List<File> files, File zipFile) throws IOException {
        File parent = zipFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create zip parent: " + parent.getAbsolutePath());
        }
        byte[] buffer = new byte[8192];
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (File file : files) {
                if (file == null || !file.exists() || !file.isFile()) {
                    continue;
                }
                ZipEntry entry = new ZipEntry(file.getName());
                output.putNextEntry(entry);
                try (FileInputStream input = new FileInputStream(file)) {
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        output.write(buffer, 0, read);
                    }
                }
                output.closeEntry();
            }
        }
    }

    public static void copy(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Unable to create target parent: " + parent.getAbsolutePath());
        }
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
    }
}
