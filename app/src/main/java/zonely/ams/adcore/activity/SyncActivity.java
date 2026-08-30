package zonely.ams.adcore.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

import androidx.core.content.ContextCompat;

import zonely.ams.adcore.R;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.scheduler.AdcoreScheduler;
import zonely.ams.adcore.scheduler.DailySyncCoordinator;
import zonely.ams.adcore.service.AdcoreSyncService;
import zonely.ams.adcore.sync.SyncProgressBroadcaster;
import zonely.ams.adcore.util.DeviceIdProvider;

public class SyncActivity extends BaseActivity {
    public static final String EXTRA_SHOW_UNMAPPED_DEVICE = "show_unmapped_device";
    public static final String EXTRA_FOREGROUND_RESOURCE_SYNC = "foreground_resource_sync";
    private static final String TAG = "SyncActivity";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, ProgressRow> rows = new LinkedHashMap<>();
    private LinearLayout list;
    private TextView headline;
    private TextView unmappedDeviceId;
    private Button refreshButton;
    private View progressContainer;
    private View unmappedContainer;
    private boolean receiverRegistered;

    private final BroadcastReceiver progressReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleProgress(intent);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        registerProgressReceiver();
        if (getIntent().getBooleanExtra(EXTRA_SHOW_UNMAPPED_DEVICE, false)) {
            showUnmappedDeviceScreen();
            return;
        }
        if (getIntent().getBooleanExtra(EXTRA_FOREGROUND_RESOURCE_SYNC, false)) {
            startForegroundResourceSync();
            return;
        }
        boolean hasCache = AdcoreDatabase.getInstance(this).hasCachedVideos();
        if (hasCache) {
            if (AdcoreScheduler.isResourceSyncDue(this)) {
                AdcoreLogger.i(TAG, "Resource metadata sync is due. Starting background sync while cached ads play.");
                AdcoreSyncService.startDaily(this, false);
            } else {
                AdcoreLogger.i(TAG, "Cached videos already exist. Starting AdsActivity. Next resource sync interval="
                        + AdcoreScheduler.resourceSyncIntervalLabel(this));
            }
            goTo(AdsActivity.class, true);
            return;
        }
        startForegroundSync();
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(progressReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    @Override
    protected void handleOnClick(View view) {
        if (view == refreshButton) {
            startForegroundSync();
        }
    }

    private void buildUi() {
        setContentView(R.layout.activity_sync);
        progressContainer = findViewById(R.id.sync_progress_container);
        unmappedContainer = findViewById(R.id.unmapped_container);
        headline = findViewById(R.id.sync_headline);
        list = findViewById(R.id.sync_progress_list);
        unmappedDeviceId = findViewById(R.id.unmapped_device_id);
        refreshButton = findViewById(R.id.unmapped_refresh_button);
        refreshButton.setOnClickListener(this);
    }

    private void registerProgressReceiver() {
        if (receiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(SyncProgressBroadcaster.ACTION_SYNC_PROGRESS);
        ContextCompat.registerReceiver(this, progressReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void startForegroundSync() {
        rows.clear();
        list.removeAllViews();
        headline.setText(R.string.syncing_resources);
        progressContainer.setVisibility(View.VISIBLE);
        unmappedContainer.setVisibility(View.GONE);
        startForegroundSyncWhenIdle(false);
    }

    private void startForegroundResourceSync() {
        DailySyncCoordinator.clearAdsRefreshPending(this);
        rows.clear();
        list.removeAllViews();
        headline.setText(R.string.syncing_resources);
        progressContainer.setVisibility(View.VISIBLE);
        unmappedContainer.setVisibility(View.GONE);
        startForegroundSyncWhenIdle(true);
    }

    private void startForegroundSyncWhenIdle(final boolean dailyPull) {
        if (AdcoreSyncService.isRunning()) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    startForegroundSyncWhenIdle(dailyPull);
                }
            }, 500L);
            return;
        }
        if (dailyPull) {
            AdcoreSyncService.startDaily(this, true);
        } else {
            AdcoreSyncService.startInitial(this, true);
        }
    }

    private void showUnmappedDeviceScreen() {
        handler.removeCallbacksAndMessages(null);
        progressContainer.setVisibility(View.GONE);
        unmappedContainer.setVisibility(View.VISIBLE);
        unmappedDeviceId.setText(DeviceIdProvider.getDeviceId(this));
    }

    private void handleProgress(Intent intent) {
        if (intent.getBooleanExtra(SyncProgressBroadcaster.EXTRA_UNMAPPED_DEVICE, false)) {
            showUnmappedDeviceScreen();
            return;
        }
        String stepId = intent.getStringExtra(SyncProgressBroadcaster.EXTRA_STEP_ID);
        String title = intent.getStringExtra(SyncProgressBroadcaster.EXTRA_TITLE);
        String status = intent.getStringExtra(SyncProgressBroadcaster.EXTRA_STATUS);
        int progress = intent.getIntExtra(SyncProgressBroadcaster.EXTRA_PROGRESS, 0);
        boolean error = intent.getBooleanExtra(SyncProgressBroadcaster.EXTRA_ERROR, false);
        boolean terminal = intent.getBooleanExtra(SyncProgressBroadcaster.EXTRA_TERMINAL, false);
        boolean success = intent.getBooleanExtra(SyncProgressBroadcaster.EXTRA_SUCCESS, false);
        if (stepId == null) {
            return;
        }
        ProgressRow row = rows.get(stepId);
        if (row == null) {
            row = new ProgressRow(title == null ? stepId : title);
            rows.put(stepId, row);
            list.addView(row.container);
        }
        row.title.setText(title == null ? stepId : title);
        row.status.setText(status == null ? "" : status);
        row.status.setTextColor(ContextCompat.getColor(this, error ? R.color.adcore_error : R.color.adcore_text_secondary));
        row.progress.setProgress(progress);
        if (terminal) {
            headline.setText(success ? R.string.sync_complete : R.string.sync_failed);
            if (success || AdcoreDatabase.getInstance(this).hasCachedVideos()) {
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        goTo(AdsActivity.class, true);
                    }
                }, 600L);
            }
        }
    }

    private class ProgressRow {
        final LinearLayout container;
        final TextView title;
        final TextView status;
        final ProgressBar progress;

        ProgressRow(String titleValue) {
            container = (LinearLayout) LayoutInflater.from(SyncActivity.this)
                    .inflate(R.layout.item_sync_progress, list, false);
            title = container.findViewById(R.id.progress_title);
            status = container.findViewById(R.id.progress_status);
            progress = container.findViewById(R.id.progress_bar);
            title.setText(titleValue);
        }
    }
}
