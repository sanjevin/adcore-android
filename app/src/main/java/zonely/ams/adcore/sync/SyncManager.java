package zonely.ams.adcore.sync;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import zonely.ams.adcore.api.ApiClient;
import zonely.ams.adcore.api.DownloadProgress;
import zonely.ams.adcore.config.AppConstants;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.model.MappedResourcesResponse;
import zonely.ams.adcore.model.ResourceItem;
import zonely.ams.adcore.model.RetryConfig;
import zonely.ams.adcore.util.AppExecutors;
import zonely.ams.adcore.util.DeviceIdProvider;
import zonely.ams.adcore.util.FileUtils;
import zonely.ams.adcore.util.TimeUtils;

public class SyncManager {
    private static final String TAG = "SyncManager";
    private final Context appContext;
    private final AdcoreDatabase db;
    private final ApiClient apiClient;

    public SyncManager(Context context) {
        this.appContext = context.getApplicationContext();
        this.db = AdcoreDatabase.getInstance(appContext);
        this.apiClient = new ApiClient(appContext);
    }

    public SyncResult runInitialSync(boolean emitProgress) {
        return runSync("INITIAL_SYNC", false, emitProgress);
    }

    public SyncResult runDailyPull(boolean emitProgress) {
        return runSync("DAILY_PULL", true, emitProgress);
    }

    private SyncResult runSync(String syncType, boolean dailyComparison, boolean emitProgress) {
        long runId = db.startSyncRun(syncType);
        long started = TimeUtils.now();
        int downloaded = 0;
        int failed = 0;
        int total = 0;
        boolean success = false;
        String terminalMessage = "";
        try {
            AdcoreLogger.i(TAG, "Sync started. type=" + syncType + " dailyComparison=" + dailyComparison);
            if (emitProgress) {
                SyncProgressBroadcaster.send(appContext, "mapped", "Mapped resources", "Starting", 0, false, false);
            }
            final String deviceId = DeviceIdProvider.getDeviceId(appContext);
            MappedResourcesResponse mapped = executeWithRetry(runId, "getMappedResources", null, new Callable<MappedResourcesResponse>() {
                @Override
                public MappedResourcesResponse call() throws Exception {
                    return apiClient.getMappedResources(deviceId);
                }
            }, emitProgress, "mapped", "Mapped resources");

            total = mapped.resources.size();
            List<ResourceItem> resourcesToDownload = dailyComparison
                    ? applyDailyComparison(mapped, emitProgress)
                    : applyInitialResourceState(mapped, emitProgress);

            DownloadSummary summary = downloadResources(runId, resourcesToDownload, emitProgress);
            downloaded = summary.downloaded;
            failed = summary.failed;
            db.setMarker(AppConstants.MARKER_LAST_DAILY_SYNC_SUCCESS, String.valueOf(TimeUtils.now()));
            success = true;
            terminalMessage = "Sync complete. resources=" + total + " downloaded=" + downloaded + " failedDownloads=" + failed;
            db.completeSyncRun(runId, true, terminalMessage);
            AdcoreLogger.i(TAG, terminalMessage + " durationMs=" + (TimeUtils.now() - started));
            if (emitProgress) {
                SyncProgressBroadcaster.terminal(appContext, true, terminalMessage);
            }
            return new SyncResult(true, terminalMessage, total, downloaded, failed);
        } catch (Exception exception) {
            failed++;
            terminalMessage = "Sync failed: " + exception.getMessage();
            db.completeSyncRun(runId, false, terminalMessage);
            AdcoreLogger.e(TAG, terminalMessage, exception);
            if (emitProgress) {
                SyncProgressBroadcaster.terminal(appContext, false, terminalMessage);
            }
            return new SyncResult(false, terminalMessage, total, downloaded, failed);
        } finally {
            AdcoreLogger.i(TAG, "Sync finished. type=" + syncType + " success=" + success + " message=" + terminalMessage);
        }
    }

    private List<ResourceItem> applyInitialResourceState(final MappedResourcesResponse mapped, boolean emitProgress) throws Exception {
        if (emitProgress) {
            SyncProgressBroadcaster.send(appContext, "db_store", "Store metadata", "Writing resources to SQLite", 25, false, false);
        }
        final Map<String, ResourceItem> existing = db.getAllResourcesById();
        final List<ResourceItem> toDownload = new ArrayList<>();
        final List<ResourceItem> checksumChanged = new ArrayList<>();
        for (ResourceItem resource : mapped.resources) {
            ResourceItem stored = existing.get(resource.id);
            if (needsDownload(resource, stored)) {
                toDownload.add(resource);
                if (stored != null && stored.localCachePath != null && !same(resource.checksum, stored.checksum)) {
                    checksumChanged.add(resource);
                }
            }
        }
        Future<?> dbWrite = AppExecutors.io().submit(new Runnable() {
            @Override
            public void run() {
                db.upsertNode(mapped.node);
                db.upsertResources(mapped.resources);
                for (ResourceItem resource : checksumChanged) {
                    db.setResourceCachePath(resource.id, null);
                }
            }
        });
        dbWrite.get();
        if (emitProgress) {
            SyncProgressBroadcaster.send(appContext, "db_store", "Store metadata",
                    "SQLite write complete; downloads pending=" + toDownload.size(), 100, true, false);
        }
        return toDownload;
    }

    private List<ResourceItem> applyDailyComparison(final MappedResourcesResponse mapped, boolean emitProgress) throws Exception {
        if (emitProgress) {
            SyncProgressBroadcaster.send(appContext, "resource_diff", "Resource diff", "Comparing server and local resource records", 10, false, false);
        }
        final Map<String, ResourceItem> existing = db.getAllResourcesById();
        final List<ResourceItem> toDownload = new ArrayList<>();
        final List<ResourceItem> toUpsert = new ArrayList<>();
        final Set<String> serverIds = new HashSet<>();
        final Map<String, ResourceItem> serverById = new HashMap<>();

        for (ResourceItem resource : mapped.resources) {
            if (resource.id == null) {
                continue;
            }
            serverIds.add(resource.id);
            serverById.put(resource.id, resource);
            ResourceItem local = existing.get(resource.id);
            if (local == null) {
                toUpsert.add(resource);
                toDownload.add(resource);
                continue;
            }
            boolean checksumChanged = !same(local.checksum, resource.checksum);
            if (checksumChanged || !local.hasSameServerProperties(resource)) {
                toUpsert.add(resource);
            }
            if (checksumChanged || needsDownload(resource, local)) {
                toDownload.add(resource);
            }
        }

        Future<?> dbWrite = AppExecutors.io().submit(new Runnable() {
            @Override
            public void run() {
                db.upsertNode(mapped.node);
                db.upsertResources(toUpsert);
                for (String localId : existing.keySet()) {
                    if (!serverIds.contains(localId)) {
                        ResourceItem local = existing.get(localId);
                        if (local != null && local.localCachePath != null) {
                            FileUtils.deleteQuietly(new File(local.localCachePath));
                        }
                        db.deleteResource(localId);
                    }
                }
                for (ResourceItem resource : toDownload) {
                    ResourceItem local = existing.get(resource.id);
                    if (local != null && !same(local.checksum, resource.checksum)) {
                        db.setResourceCachePath(resource.id, null);
                    }
                }
            }
        });
        dbWrite.get();
        if (emitProgress) {
            SyncProgressBroadcaster.send(appContext, "resource_diff", "Resource diff",
                    "Diff complete; changed/new downloads=" + toDownload.size(), 100, true, false);
        }
        AdcoreLogger.i(TAG, "Daily diff complete. serverResources=" + serverById.size() + " localResources=" + existing.size()
                + " upserts=" + toUpsert.size() + " downloads=" + toDownload.size());
        return toDownload;
    }

    private DownloadSummary downloadResources(long runId, List<ResourceItem> resources, final boolean emitProgress) throws Exception {
        if (resources.isEmpty()) {
            if (emitProgress) {
                SyncProgressBroadcaster.send(appContext, "downloads", "Downloads", "No downloads needed", 100, true, false);
            }
            return new DownloadSummary(0, 0);
        }
        int threadCount = Math.min(db.getDownloadThreadCount(), resources.size());
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        final AtomicInteger completed = new AtomicInteger(0);
        final AtomicInteger downloaded = new AtomicInteger(0);
        final AtomicInteger failed = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();
        for (final ResourceItem resource : resources) {
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    String title = resource.fileName == null ? resource.id : resource.fileName;
                    String stepId = "download_" + resource.id;
                    try {
                        if (emitProgress) {
                            SyncProgressBroadcaster.send(appContext, stepId, title, "Queued", 0, false, false);
                        }
                        downloadOne(runId, resource, emitProgress, stepId, title);
                        downloaded.incrementAndGet();
                        if (emitProgress) {
                            SyncProgressBroadcaster.send(appContext, stepId, title, "Downloaded", 100, true, false);
                        }
                    } catch (Exception exception) {
                        failed.incrementAndGet();
                        AdcoreLogger.e(TAG, "Resource download failed after retries. resourceId=" + resource.id, exception);
                        if (emitProgress) {
                            SyncProgressBroadcaster.send(appContext, stepId, title,
                                    "Failed: " + exception.getMessage(), 100, true, true);
                        }
                    } finally {
                        int done = completed.incrementAndGet();
                        if (emitProgress) {
                            int progress = (int) ((done * 100L) / resources.size());
                            SyncProgressBroadcaster.send(appContext, "downloads", "Downloads",
                                    "Completed " + done + " of " + resources.size(), progress, done == resources.size(), false);
                        }
                    }
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();
        return new DownloadSummary(downloaded.get(), failed.get());
    }

    private void downloadOne(final long runId, final ResourceItem resource, final boolean emitProgress,
                             final String stepId, final String title) throws Exception {
        final File targetFile = resourceTargetFile(resource);
        executeWithRetry(runId, "downloadResource", resource.id, new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                apiClient.downloadResource(resource.id, targetFile, new DownloadProgress() {
                    @Override
                    public void onProgress(long bytesRead, long totalBytes) {
                        if (!emitProgress) {
                            return;
                        }
                        int percent = totalBytes > 0 ? (int) ((bytesRead * 100L) / totalBytes) : 50;
                        SyncProgressBroadcaster.send(appContext, stepId, title,
                                "Downloading " + bytesRead + "/" + (totalBytes > 0 ? totalBytes : -1) + " bytes",
                                percent, false, false);
                    }
                });
                db.setResourceCachePath(resource.id, targetFile.getAbsolutePath());
                return null;
            }
        }, emitProgress, stepId, title);
    }

    private <T> T executeWithRetry(long runId, String apiName, String resourceId, Callable<T> callable,
                                   boolean emitProgress, String stepId, String title) throws Exception {
        RetryConfig retry = db.getApiRetryConfig();
        int attempt = 0;
        Exception last = null;
        while (attempt <= retry.maxRetries) {
            long started = TimeUtils.now();
            try {
                if (emitProgress) {
                    SyncProgressBroadcaster.send(appContext, stepId, title,
                            "Attempt " + (attempt + 1) + " of " + (retry.maxRetries + 1),
                            attempt == 0 ? 10 : 30, false, false);
                }
                T result = callable.call();
                long completed = TimeUtils.now();
                db.recordApiMetric(runId, apiName, resourceId, started, completed, attempt, true, "OK");
                if (emitProgress) {
                    SyncProgressBroadcaster.send(appContext, stepId, title, "Completed", 100, true, false);
                }
                return result;
            } catch (Exception exception) {
                last = exception;
                long completed = TimeUtils.now();
                db.recordApiMetric(runId, apiName, resourceId, started, completed, attempt, false, exception.getMessage());
                AdcoreLogger.w(TAG, apiName + " failed. resourceId=" + resourceId + " attempt=" + (attempt + 1)
                        + " maxAttempts=" + (retry.maxRetries + 1) + " message=" + exception.getMessage(), exception);
                if (attempt >= retry.maxRetries) {
                    break;
                }
                if (emitProgress) {
                    SyncProgressBroadcaster.send(appContext, stepId, title,
                            "Retrying after failure: " + exception.getMessage(), 40, false, true);
                }
                sleepSeconds(retry.delaySeconds);
                attempt++;
            }
        }
        throw last == null ? new IllegalStateException(apiName + " failed") : last;
    }

    private boolean needsDownload(ResourceItem server, ResourceItem stored) {
        if (server == null || server.id == null) {
            return false;
        }
        if (stored == null || stored.localCachePath == null || stored.localCachePath.length() == 0) {
            return true;
        }
        File file = new File(stored.localCachePath);
        if (!file.exists() || !file.isFile() || file.length() == 0L) {
            return true;
        }
        return !same(server.checksum, stored.checksum);
    }

    private File resourceTargetFile(ResourceItem resource) {
        String base = FileUtils.sanitizeFileName(resource.id);
        String extension = extensionFor(resource);
        return new File(FileUtils.resourcesDir(appContext), base + extension);
    }

    private String extensionFor(ResourceItem resource) {
        if (resource.fileName != null) {
            int index = resource.fileName.lastIndexOf('.');
            if (index >= 0 && index < resource.fileName.length() - 1) {
                String extension = resource.fileName.substring(index);
                if (extension.length() <= 8) {
                    return extension;
                }
            }
        }
        if (resource.mediaType != null && resource.mediaType.toLowerCase(Locale.ROOT).contains("mp4")) {
            return ".mp4";
        }
        return ".mp4";
    }

    private boolean same(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private void sleepSeconds(int seconds) {
        if (seconds <= 0) {
            return;
        }
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class DownloadSummary {
        final int downloaded;
        final int failed;

        DownloadSummary(int downloaded, int failed) {
            this.downloaded = downloaded;
            this.failed = failed;
        }
    }
}
