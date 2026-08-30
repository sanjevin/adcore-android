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

import zonely.ams.adcore.R;
import zonely.ams.adcore.api.ApiClient;
import zonely.ams.adcore.api.ApiException;
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
        final String deviceId = DeviceIdProvider.getDeviceId(appContext);
        try {
            AdcoreLogger.i(TAG, "Sync started. type=" + syncType + " dailyComparison=" + dailyComparison);
            if (emitProgress) {
                SyncProgressBroadcaster.send(appContext, "mapped", appContext.getString(R.string.mapped_resources),
                        appContext.getString(R.string.progress_starting), 0, false, false);
            }
            MappedResourcesResponse mapped = executeWithRetry(runId, "getMappedResources", null, new Callable<MappedResourcesResponse>() {
                @Override
                public MappedResourcesResponse call() throws Exception {
                    return apiClient.getMappedResources(deviceId);
                }
            }, emitProgress, "mapped", appContext.getString(R.string.mapped_resources));
            validateMappedDevice(deviceId, mapped);

            total = mapped.resources.size();
            List<ResourceItem> resourcesToDownload = dailyComparison
                    ? applyDailyComparison(mapped, emitProgress)
                    : applyInitialResourceState(mapped, emitProgress);

            DownloadSummary summary = downloadResources(runId, resourcesToDownload, emitProgress);
            downloaded = summary.downloaded;
            failed = summary.failed;
            if (summary.connectivityFailures > 0) {
                terminalMessage = "Sync skipped because internet/server is unavailable.";
                db.completeSyncRun(runId, false, terminalMessage);
                AdcoreLogger.w(TAG, terminalMessage + " connectivityFailures=" + summary.connectivityFailures);
                return new SyncResult(false, terminalMessage, total, downloaded, failed, false, true);
            }
            db.setMarker(AppConstants.MARKER_LAST_RESOURCE_SYNC_SUCCESS, String.valueOf(TimeUtils.now()));
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
            boolean connectivityFailure = ApiClient.isConnectivityFailure(exception);
            if (connectivityFailure) {
                terminalMessage = exception.getMessage() == null
                        ? "Sync skipped because internet/server is unavailable."
                        : exception.getMessage();
                db.completeSyncRun(runId, false, terminalMessage);
                AdcoreLogger.w(TAG, terminalMessage, exception);
                return new SyncResult(false, terminalMessage, total, downloaded, failed, false, true);
            }
            boolean unmappedDevice = isUnmappedDevice(exception);
            if (unmappedDevice) {
                clearMappedStateForUnmappedDevice(deviceId);
                terminalMessage = "Device not configured. deviceId=" + deviceId;
            } else {
                terminalMessage = "Sync failed: " + exception.getMessage();
            }
            db.completeSyncRun(runId, false, terminalMessage);
            AdcoreLogger.e(TAG, terminalMessage, exception);
            if (unmappedDevice) {
                SyncProgressBroadcaster.unmappedDevice(appContext, terminalMessage);
            } else if (emitProgress) {
                SyncProgressBroadcaster.terminal(appContext, false, terminalMessage);
            }
            return new SyncResult(false, terminalMessage, total, downloaded, failed, unmappedDevice);
        } finally {
            AdcoreLogger.i(TAG, "Sync finished. type=" + syncType + " success=" + success + " message=" + terminalMessage);
        }
    }

    private List<ResourceItem> applyInitialResourceState(final MappedResourcesResponse mapped, boolean emitProgress) throws Exception {
        if (emitProgress) {
            SyncProgressBroadcaster.send(appContext, "db_store", appContext.getString(R.string.store_metadata),
                    appContext.getString(R.string.progress_writing_resources_to_sqlite), 25, false, false);
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
            SyncProgressBroadcaster.send(appContext, "db_store", appContext.getString(R.string.store_metadata),
                    appContext.getString(R.string.progress_sqlite_write_complete, toDownload.size()), 100, true, false);
        }
        return toDownload;
    }

    private List<ResourceItem> applyDailyComparison(final MappedResourcesResponse mapped, boolean emitProgress) throws Exception {
        if (emitProgress) {
            SyncProgressBroadcaster.send(appContext, "resource_diff", appContext.getString(R.string.resource_diff),
                    appContext.getString(R.string.progress_comparing_resources), 10, false, false);
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
                toDownload.add(resource);
                continue;
            }
            boolean checksumChanged = !same(local.checksum, resource.checksum);
            boolean downloadNeeded = checksumChanged || needsDownload(resource, local);
            if (checksumChanged) {
                toDownload.add(resource);
                continue;
            }
            if (!local.hasSameServerProperties(resource)) {
                toUpsert.add(resource);
            }
            if (downloadNeeded) {
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
                            AdcoreLogger.i(TAG, "Resource removed from server; DB entry deleted and cached file left "
                                    + "for playback-safe cleanup. resourceId=" + localId
                                    + " path=" + local.localCachePath);
                        }
                        db.deleteResource(localId);
                    }
                }
            }
        });
        dbWrite.get();
        if (emitProgress) {
            SyncProgressBroadcaster.send(appContext, "resource_diff", appContext.getString(R.string.resource_diff),
                    appContext.getString(R.string.progress_diff_complete, toDownload.size()), 100, true, false);
        }
        AdcoreLogger.i(TAG, "Resource diff complete. serverResources=" + serverById.size()
                + " localResources=" + existing.size()
                + " upserts=" + toUpsert.size() + " downloads=" + toDownload.size());
        return toDownload;
    }

    private DownloadSummary downloadResources(long runId, List<ResourceItem> resources, final boolean emitProgress) throws Exception {
        prepareResourceCache(emitProgress);
        if (resources.isEmpty()) {
            if (emitProgress) {
                SyncProgressBroadcaster.send(appContext, "downloads", appContext.getString(R.string.downloads),
                        appContext.getString(R.string.progress_no_downloads_needed), 100, true, false);
            }
            return new DownloadSummary(0, 0, 0);
        }
        int threadCount = Math.min(db.getDownloadThreadCount(), resources.size());
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        final AtomicInteger completed = new AtomicInteger(0);
        final AtomicInteger downloaded = new AtomicInteger(0);
        final AtomicInteger failed = new AtomicInteger(0);
        final AtomicInteger connectivityFailures = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();
        for (final ResourceItem resource : resources) {
            futures.add(pool.submit(new Runnable() {
                @Override
                public void run() {
                    String title = resource.fileName == null ? resource.id : resource.fileName;
                    String stepId = "download_" + resource.id;
                    try {
                        if (emitProgress) {
                            SyncProgressBroadcaster.send(appContext, stepId, title,
                                    appContext.getString(R.string.progress_queued), 0, false, false);
                        }
                        downloadOne(runId, resource, emitProgress, stepId, title);
                        downloaded.incrementAndGet();
                        if (emitProgress) {
                            SyncProgressBroadcaster.send(appContext, stepId, title,
                                    appContext.getString(R.string.progress_downloaded), 100, true, false);
                        }
                    } catch (Exception exception) {
                        failed.incrementAndGet();
                        boolean connectivityFailure = ApiClient.isConnectivityFailure(exception);
                        if (connectivityFailure) {
                            connectivityFailures.incrementAndGet();
                            AdcoreLogger.w(TAG, "Resource download skipped because internet/server is unavailable. "
                                    + resourceMetadata(resource), exception);
                        } else {
                            AdcoreLogger.e(TAG, "Resource download failed after retries. " + resourceMetadata(resource), exception);
                        }
                        if (emitProgress && !connectivityFailure) {
                            SyncProgressBroadcaster.send(appContext, stepId, title,
                                    appContext.getString(R.string.progress_failed_with_message, exception.getMessage()), 100, true, true);
                        }
                    } finally {
                        int done = completed.incrementAndGet();
                        if (emitProgress) {
                            int progress = (int) ((done * 100L) / resources.size());
                            SyncProgressBroadcaster.send(appContext, "downloads", appContext.getString(R.string.downloads),
                                    appContext.getString(R.string.progress_completed_count, done, resources.size()),
                                    progress, done == resources.size(), false);
                        }
                    }
                }
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();
        return new DownloadSummary(downloaded.get(), failed.get(), connectivityFailures.get());
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
                                appContext.getString(R.string.progress_downloading_bytes,
                                        bytesRead, totalBytes > 0 ? totalBytes : -1),
                                percent, false, false);
                    }
                });
                db.upsertResource(resource);
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
                            appContext.getString(R.string.progress_attempt_count, attempt + 1, retry.maxRetries + 1),
                            attempt == 0 ? 10 : 30, false, false);
                }
                T result = callable.call();
                long completed = TimeUtils.now();
                db.recordApiMetric(runId, apiName, resourceId, started, completed, attempt, true, "OK");
                if (emitProgress) {
                    SyncProgressBroadcaster.send(appContext, stepId, title,
                            appContext.getString(R.string.progress_completed), 100, true, false);
                }
                return result;
            } catch (Exception exception) {
                last = exception;
                long completed = TimeUtils.now();
                db.recordApiMetric(runId, apiName, resourceId, started, completed, attempt, false, exception.getMessage());
                AdcoreLogger.w(TAG, apiName + " failed. resourceId=" + resourceId + " attempt=" + (attempt + 1)
                        + " maxAttempts=" + (retry.maxRetries + 1) + " message=" + exception.getMessage(), exception);
                if (attempt >= retry.maxRetries || isUnmappedDevice(exception) || ApiClient.isConnectivityFailure(exception)) {
                    break;
                }
                if (emitProgress) {
                    SyncProgressBroadcaster.send(appContext, stepId, title,
                            appContext.getString(R.string.progress_retrying_after_failure, exception.getMessage()),
                            40, false, true);
                }
                sleepSeconds(retry.delaySeconds);
                attempt++;
            }
        }
        throw last == null ? new IllegalStateException(apiName + " failed") : last;
    }

    private void prepareResourceCache(boolean emitProgress) throws Exception {
        if (emitProgress) {
            SyncProgressBroadcaster.send(appContext, "cache_ready", appContext.getString(R.string.video_cache),
                    appContext.getString(R.string.progress_preparing_cache_folder), 5, false, false);
        }
        File dir = FileUtils.resourcesDir(appContext);
        FileUtils.ensureWritableDirectory(dir);
        if (emitProgress) {
            SyncProgressBroadcaster.send(appContext, "cache_ready", appContext.getString(R.string.video_cache),
                    appContext.getString(R.string.progress_cache_ready, dir.getAbsolutePath()), 100, true, false);
        }
        AdcoreLogger.i(TAG, "Video cache ready for downloads. path=" + dir.getAbsolutePath());
    }

    private void validateMappedDevice(String localDeviceId, MappedResourcesResponse mapped) {
        String mappedDeviceId = mapped == null || mapped.node == null ? null : mapped.node.deviceId;
        if (mappedDeviceId == null || mappedDeviceId.trim().length() == 0) {
            return;
        }
        if (!localDeviceId.equals(mappedDeviceId.trim())) {
            throw new IllegalStateException("Mapped node deviceId mismatch. localDeviceId=" + localDeviceId
                    + " mappedDeviceId=" + mappedDeviceId);
        }
    }

    private boolean isUnmappedDevice(Exception exception) {
        if (exception instanceof ApiException) {
            ApiException apiException = (ApiException) exception;
            return apiException.getHttpCode() == 404 && containsDeviceNotConfigured(apiException.getMessage());
        }
        Throwable cause = exception == null ? null : exception.getCause();
        while (cause != null) {
            if (cause instanceof ApiException) {
                ApiException apiException = (ApiException) cause;
                return apiException.getHttpCode() == 404 && containsDeviceNotConfigured(apiException.getMessage());
            }
            cause = cause.getCause();
        }
        return false;
    }

    private boolean containsDeviceNotConfigured(String message) {
        return message != null && message.toLowerCase(Locale.ROOT).contains("device not configured");
    }

    private void clearMappedStateForUnmappedDevice(String deviceId) {
        File resourceDir = FileUtils.resourcesDir(appContext);
        FileUtils.deleteDirectoryContents(resourceDir);
        db.clearMappedResourcesAndNodes();
        AdcoreLogger.w(TAG, "Device is not configured; local cached videos and mapped resource state cleared. deviceId="
                + deviceId + " resourceDir=" + resourceDir.getAbsolutePath());
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
        String base = FileUtils.sanitizeFileName(resource == null ? null : resource.id);
        String version = resource == null ? null : resource.checksum;
        if (version == null || version.trim().length() == 0) {
            version = resource == null ? null : resource.updatedAt;
        }
        if (version != null && version.trim().length() > 0) {
            String safeVersion = FileUtils.sanitizeFileName(version.trim());
            if (safeVersion.length() > 32) {
                safeVersion = safeVersion.substring(0, 32);
            }
            base = base + "_" + safeVersion;
        }
        String extension = extensionFor(resource);
        return new File(FileUtils.resourcesDir(appContext), base + extension);
    }

    private String resourceMetadata(ResourceItem resource) {
        if (resource == null) {
            return "resource=null";
        }
        return "resourceId=" + resource.id
                + " fileName=" + resource.fileName
                + " mediaType=" + resource.mediaType
                + " fileSizeBytes=" + resource.fileSizeBytes
                + " checksum=" + resource.checksum
                + " durationSeconds=" + resource.durationSeconds
                + " typeKey=" + resource.typeKey
                + " status=" + resource.status
                + " displayOrder=" + resource.displayOrder
                + " fileUri=" + resource.fileUri
                + " targetFile=" + resourceTargetFile(resource).getAbsolutePath();
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
        final int connectivityFailures;

        DownloadSummary(int downloaded, int failed, int connectivityFailures) {
            this.downloaded = downloaded;
            this.failed = failed;
            this.connectivityFailures = connectivityFailures;
        }
    }
}
