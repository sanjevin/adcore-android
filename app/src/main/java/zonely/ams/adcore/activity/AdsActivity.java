package zonely.ams.adcore.activity;

import android.graphics.Color;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.VideoView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.model.ResourceItem;
import zonely.ams.adcore.service.AdcoreSyncService;
import zonely.ams.adcore.util.TimeUtils;

public class AdsActivity extends BaseActivity {
    private static final String TAG = "AdsActivity";
    private VideoView videoView;
    private TextView emptyMessage;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<ResourceItem> playlist = new ArrayList<>();
    private int index;
    private long currentStartMs;
    private ResourceItem currentResource;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Root container
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Inner container to handle centering
        LinearLayout centerWrapper = new LinearLayout(this);
        centerWrapper.setGravity(Gravity.CENTER);

        videoView = new VideoView(this);

        // Add VideoView to the wrapper (Wrap content so it doesn't force itself to top)
        centerWrapper.addView(videoView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Add the wrapper to the root FrameLayout
        root.addView(centerWrapper, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        emptyMessage = label("", 22, Color.WHITE);
        emptyMessage.setGravity(Gravity.CENTER);
        root.addView(emptyMessage, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(root);
        wireVideoCallbacks();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadPlaylistAndPlay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null && videoView.isPlaying()) {
            videoView.pause();
        }
    }

    private void wireVideoCallbacks() {
        videoView.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                mp.setLooping(false);
                currentStartMs = TimeUtils.now();
                emptyMessage.setText("");
                videoView.start();
                AdcoreLogger.i(TAG, "Video started. resourceId=" + (currentResource == null ? null : currentResource.id));
            }
        });
        videoView.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mp) {
                recordPlaybackAndContinue();
            }
        });
        videoView.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, int what, int extra) {
                AdcoreLogger.e(TAG, "Video playback error. resourceId=" + (currentResource == null ? null : currentResource.id)
                        + " what=" + what + " extra=" + extra);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        playNext();
                    }
                });
                return true;
            }
        });
    }

    private void loadPlaylistAndPlay() {
        playlist.clear();
        playlist.addAll(AdcoreDatabase.getInstance(this).getCachedResources());
        if (playlist.isEmpty()) {
            emptyMessage.setText("No cached videos available");
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

    private void playCurrent() {
        if (playlist.isEmpty()) {
            loadPlaylistAndPlay();
            return;
        }
        currentResource = playlist.get(index);
        if (currentResource.localCachePath == null || !new File(currentResource.localCachePath).exists()) {
            AdcoreLogger.w(TAG, "Cached file missing during playback. resourceId=" + currentResource.id);
            playNext();
            return;
        }
        videoView.setVideoURI(Uri.fromFile(new File(currentResource.localCachePath)));
    }

    private void recordPlaybackAndContinue() {
        if (currentResource != null) {
            int playedSeconds = currentResource.durationSeconds > 0
                    ? currentResource.durationSeconds
                    : (int) Math.max(0L, (TimeUtils.now() - currentStartMs) / 1000L);
            AdcoreDatabase.getInstance(this).incrementPlayback(currentResource.id, playedSeconds);
            AdcoreLogger.i(TAG, "Video completed. resourceId=" + currentResource.id + " playedSeconds=" + playedSeconds);
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
}
