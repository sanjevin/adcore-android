package zonely.ams.adcore.activity;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import androidx.core.content.ContextCompat;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import zonely.ams.adcore.R;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.install.InstallState;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.model.ResourceItem;
import zonely.ams.adcore.scheduler.DailySyncCoordinator;
import zonely.ams.adcore.service.AdcoreSyncService;
import zonely.ams.adcore.sync.SyncProgressBroadcaster;
import zonely.ams.adcore.util.DeviceIdProvider;
import zonely.ams.adcore.util.FileUtils;
import zonely.ams.adcore.util.TimeUtils;

@SuppressLint("GestureBackNavigation")
public class AdsActivity extends BaseActivity {
    private static final String TAG = "AdsActivity";
    private static final long DEVICE_ID_OVERLAY_MS = 60000L;
    private static volatile boolean active;
    private VLCVideoLayout videoLayout;
    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private TextView emptyMessage;
    private TextView deviceIdText;
    private View deviceIdOverlay;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ResourceItem> playlist = new ArrayList<>();
    private boolean viewsAttached;
    private boolean receiverRegistered;
    private boolean resourceSyncReceiverRegistered;
    private boolean deviceIdOverlayVisible;
    private boolean resourceRefreshPendingAfterCurrent;
    private int index;
    private long currentStartMs;
    private ResourceItem currentResource;

    private final BroadcastReceiver syncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && intent.getBooleanExtra(SyncProgressBroadcaster.EXTRA_UNMAPPED_DEVICE, false)) {
                openUnmappedDeviceScreen();
            }
        }
    };

    private final BroadcastReceiver resourceSyncReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && DailySyncCoordinator.ACTION_RESOURCE_SYNC_COMPLETED.equals(intent.getAction())) {
                handleResourceSyncCompleted();
            }
        }
    };

    private final Runnable hideDeviceIdOverlayRunnable = new Runnable() {
        @Override
        public void run() {
            hideDeviceIdOverlay();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ads);
        videoLayout = findViewById(R.id.vlc_video_layout);
        emptyMessage = findViewById(R.id.ads_empty_message);
        deviceIdOverlay = findViewById(R.id.ads_device_id_overlay);
        deviceIdText = findViewById(R.id.ads_device_id);
        initVlcPlayer();
    }

    @Override
    protected void onStart() {
        super.onStart();
        active = true;
        attachPlayerViews();
        registerSyncReceiver();
        registerResourceSyncReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (holdPlaybackForInstall()) {
            return;
        }
        if (DailySyncCoordinator.isAdsRefreshPending(this)) {
            handleResourceSyncCompleted();
            return;
        }
        if (!deviceIdOverlayVisible) {
            loadPlaylistAndPlay();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pausePlayback();
    }

    @Override
    protected void onStop() {
        active = false;
        unregisterSyncReceiver();
        unregisterResourceSyncReceiver();
        handler.removeCallbacks(hideDeviceIdOverlayRunnable);
        deviceIdOverlayVisible = false;
        deviceIdOverlay.setVisibility(View.GONE);
        stopPlayback();
        detachPlayerViews();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        releaseVlcPlayer();
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            event.startTracking();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            showDeviceIdOverlay();
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        AdcoreLogger.i(TAG, "Short Back press consumed on AdsActivity.");
    }

    private void initVlcPlayer() {
        List<String> options = new ArrayList<>();
        options.add("--no-sub-autodetect-file");
        options.add("--audio-time-stretch");
        options.add("--avcodec-hw=none");
        libVLC = new LibVLC(this, options);
        mediaPlayer = new MediaPlayer(libVLC);
        mediaPlayer.setEventListener(new MediaPlayer.EventListener() {
            @Override
            public void onEvent(final MediaPlayer.Event event) {
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        handlePlayerEvent(event);
                    }
                });
            }
        });
    }

    private void handlePlayerEvent(MediaPlayer.Event event) {
        if (event == null) {
            return;
        }
        if (holdPlaybackForInstall()) {
            return;
        }
        switch (event.type) {
            case MediaPlayer.Event.Playing:
                currentStartMs = TimeUtils.now();
                emptyMessage.setText("");
                AdcoreLogger.i(TAG, "Video started with LibVLC. resourceId="
                        + (currentResource == null ? null : currentResource.id));
                break;
            case MediaPlayer.Event.EndReached:
                recordPlaybackAndContinue();
                break;
            case MediaPlayer.Event.EncounteredError:
                AdcoreLogger.e(TAG, "LibVLC playback error. resourceId="
                        + (currentResource == null ? null : currentResource.id)
                        + " file=" + (currentResource == null ? null : currentResource.localCachePath));
                if (isResourceRefreshPending()) {
                    restartAdsActivityForSyncedResources();
                } else {
                    playNext();
                }
                break;
            default:
                break;
        }
    }

    private void attachPlayerViews() {
        if (!viewsAttached && mediaPlayer != null) {
            mediaPlayer.attachViews(videoLayout, null, false, false);
            viewsAttached = true;
        }
    }

    private void detachPlayerViews() {
        if (viewsAttached && mediaPlayer != null) {
            mediaPlayer.detachViews();
            viewsAttached = false;
        }
    }

    private void loadPlaylistAndPlay() {
        if (holdPlaybackForInstall()) {
            return;
        }
        if (deviceIdOverlayVisible) {
            return;
        }
        playlist.clear();
        playlist.addAll(AdcoreDatabase.getInstance(this).getCachedResources());
        cleanupOrphanResourceFilesWhenIdle(playlist);
        if (playlist.isEmpty()) {
            emptyMessage.setText(R.string.no_cached_videos_available);
            stopPlayback();
            AdcoreLogger.w(TAG, "No cached videos found. Starting foreground sync.");
            AdcoreSyncService.startInitial(this, true);
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    goTo(SyncActivity.class, true);
                }
            }, 1500L);
            return;
        }
        index = Math.max(0, index % playlist.size());
        playCurrent();
    }

    private void cleanupOrphanResourceFilesWhenIdle(List<ResourceItem> activeResources) {
        if (AdcoreSyncService.isRunning()) {
            return;
        }
        File dir = FileUtils.resourcesDir(this);
        File[] files = dir.listFiles();
        if (files == null || files.length == 0) {
            return;
        }
        Set<String> activePaths = new HashSet<>();
        for (ResourceItem resource : activeResources) {
            if (resource.localCachePath != null && resource.localCachePath.length() > 0) {
                activePaths.add(new File(resource.localCachePath).getAbsolutePath());
            }
        }
        for (File file : files) {
            if (!file.isFile() || file.getName().endsWith(".download")) {
                continue;
            }
            if (!activePaths.contains(file.getAbsolutePath())) {
                FileUtils.deleteQuietly(file);
                AdcoreLogger.i(TAG, "Deleted orphaned resource cache file after playback refresh. path="
                        + file.getAbsolutePath());
            }
        }
    }

    private void playCurrent() {
        if (holdPlaybackForInstall()) {
            return;
        }
        if (playlist.isEmpty()) {
            loadPlaylistAndPlay();
            return;
        }
        attachPlayerViews();
        currentResource = playlist.get(index);
        if (currentResource.localCachePath == null || !new File(currentResource.localCachePath).exists()) {
            AdcoreLogger.w(TAG, "Cached file missing during playback. resourceId=" + currentResource.id);
            playNext();
            return;
        }
        File localFile = new File(currentResource.localCachePath);
        if (mediaPlayer.hasMedia()) {
            mediaPlayer.stop();
        }
        Media media = new Media(libVLC, Uri.fromFile(localFile));
        media.setHWDecoderEnabled(false, false);
        mediaPlayer.setMedia(media);
        media.release();
        mediaPlayer.play();
    }

    private void recordPlaybackAndContinue() {
        if (currentResource != null) {
            int playedSeconds = currentResource.durationSeconds > 0
                    ? currentResource.durationSeconds
                    : (int) Math.max(0L, (TimeUtils.now() - currentStartMs) / 1000L);
            AdcoreDatabase.getInstance(this).incrementPlayback(currentResource.id, playedSeconds);
            AdcoreLogger.i(TAG, "Video completed. resourceId=" + currentResource.id + " playedSeconds=" + playedSeconds);
        }
        if (isResourceRefreshPending()) {
            restartAdsActivityForSyncedResources();
            return;
        }
        playNext();
    }

    private void playNext() {
        if (playlist.isEmpty()) {
            loadPlaylistAndPlay();
            return;
        }
        index++;
        if (index >= playlist.size()) {
            index = 0;
            playlist.clear();
            playlist.addAll(AdcoreDatabase.getInstance(this).getCachedResources());
            if (playlist.isEmpty()) {
                loadPlaylistAndPlay();
                return;
            }
        }
        playCurrent();
    }

    private void showDeviceIdOverlay() {
        pausePlayback();
        deviceIdText.setText(DeviceIdProvider.getDeviceId(this));
        deviceIdOverlayVisible = true;
        deviceIdOverlay.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideDeviceIdOverlayRunnable);
        handler.postDelayed(hideDeviceIdOverlayRunnable, DEVICE_ID_OVERLAY_MS);
        AdcoreLogger.i(TAG, "DeviceId overlay shown from long Back press.");
    }

    private void hideDeviceIdOverlay() {
        if (!deviceIdOverlayVisible) {
            return;
        }
        deviceIdOverlayVisible = false;
        deviceIdOverlay.setVisibility(View.GONE);
        if (holdPlaybackForInstall()) {
            return;
        }
        if (currentResource != null && currentResource.localCachePath != null
                && new File(currentResource.localCachePath).exists()) {
            mediaPlayer.play();
        } else {
            loadPlaylistAndPlay();
        }
        AdcoreLogger.i(TAG, "DeviceId overlay hidden; playback resumed.");
    }

    private void pausePlayback() {
        if (mediaPlayer != null && mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
        }
    }

    private boolean holdPlaybackForInstall() {
        if (!InstallState.isPending(this)) {
            return false;
        }
        pausePlayback();
        emptyMessage.setText(R.string.update_install_waiting);
        File apkFile = InstallState.getPendingApk(this);
        if (apkFile != null) {
            InstallPromptActivity.start(this, apkFile);
        }
        AdcoreLogger.i(TAG, "Playback held because update install is pending.");
        return true;
    }

    private void stopPlayback() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
        }
    }

    private void releaseVlcPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.setEventListener(null);
            mediaPlayer.release();
            mediaPlayer = null;
        }
        if (libVLC != null) {
            libVLC.release();
            libVLC = null;
        }
        viewsAttached = false;
    }

    private void registerSyncReceiver() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(SyncProgressBroadcaster.ACTION_SYNC_PROGRESS);
        ContextCompat.registerReceiver(this, syncReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void unregisterSyncReceiver() {
        if (receiverRegistered) {
            unregisterReceiver(syncReceiver);
            receiverRegistered = false;
        }
    }

    private void registerResourceSyncReceiver() {
        if (resourceSyncReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(DailySyncCoordinator.ACTION_RESOURCE_SYNC_COMPLETED);
        ContextCompat.registerReceiver(this, resourceSyncReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        resourceSyncReceiverRegistered = true;
    }

    private void unregisterResourceSyncReceiver() {
        if (resourceSyncReceiverRegistered) {
            unregisterReceiver(resourceSyncReceiver);
            resourceSyncReceiverRegistered = false;
        }
    }

    private void handleResourceSyncCompleted() {
        if (shouldWaitForCurrentVideoBeforeResourceRefresh()) {
            resourceRefreshPendingAfterCurrent = true;
            AdcoreLogger.i(TAG, "Background resource sync completed; waiting for current video to complete before refresh. resourceId="
                    + (currentResource == null ? null : currentResource.id));
            return;
        }
        restartAdsActivityForSyncedResources();
    }

    private boolean shouldWaitForCurrentVideoBeforeResourceRefresh() {
        return currentResource != null
                && mediaPlayer != null
                && (mediaPlayer.isPlaying() || deviceIdOverlayVisible);
    }

    private boolean isResourceRefreshPending() {
        return resourceRefreshPendingAfterCurrent || DailySyncCoordinator.isAdsRefreshPending(this);
    }

    private void restartAdsActivityForSyncedResources() {
        resourceRefreshPendingAfterCurrent = false;
        DailySyncCoordinator.clearAdsRefreshPending(this);
        handler.removeCallbacksAndMessages(null);
        closePlaybackForResourceRefresh();
        AdcoreLogger.i(TAG, "Restarting AdsActivity after background resource sync.");
        Intent intent = new Intent(this, AdsActivity.class);
        startActivity(intent);
        finish();
    }

    private void closePlaybackForResourceRefresh() {
        deviceIdOverlayVisible = false;
        if (deviceIdOverlay != null) {
            deviceIdOverlay.setVisibility(View.GONE);
        }
        stopPlayback();
        detachPlayerViews();
        releaseVlcPlayer();
    }

    public static boolean isActive() {
        return active;
    }

    private void openUnmappedDeviceScreen() {
        handler.removeCallbacksAndMessages(null);
        stopPlayback();
        AdcoreLogger.w(TAG, "Unmapped device detected while ads were playing; opening SyncActivity mapping screen.");
        Intent intent = new Intent(this, SyncActivity.class);
        intent.putExtra(SyncActivity.EXTRA_SHOW_UNMAPPED_DEVICE, true);
        startActivity(intent);
        finish();
    }
}
