package zonely.ams.adcore.sync;

public class SyncResult {
    public final boolean success;
    public final String message;
    public final int totalResources;
    public final int downloadedResources;
    public final int failedDownloads;
    public final boolean unmappedDevice;

    public SyncResult(boolean success, String message, int totalResources, int downloadedResources, int failedDownloads) {
        this(success, message, totalResources, downloadedResources, failedDownloads, false);
    }

    public SyncResult(boolean success, String message, int totalResources, int downloadedResources,
                      int failedDownloads, boolean unmappedDevice) {
        this.success = success;
        this.message = message;
        this.totalResources = totalResources;
        this.downloadedResources = downloadedResources;
        this.failedDownloads = failedDownloads;
        this.unmappedDevice = unmappedDevice;
    }
}
