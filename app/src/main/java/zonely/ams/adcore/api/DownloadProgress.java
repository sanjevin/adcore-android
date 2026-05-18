package zonely.ams.adcore.api;

public interface DownloadProgress {
    void onProgress(long bytesRead, long totalBytes);
}
