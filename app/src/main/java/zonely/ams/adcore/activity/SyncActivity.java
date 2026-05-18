package zonely.ams.adcore.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.LinkedHashMap;
import java.util.Map;

import androidx.core.content.ContextCompat;

import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.scheduler.AdcoreScheduler;
import zonely.ams.adcore.service.AdcoreSyncService;
import zonely.ams.adcore.sync.SyncProgressBroadcaster;

public class SyncActivity extends BaseActivity {
    private static final String TAG = "SyncActivity";
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, ProgressRow> rows = new LinkedHashMap<>();
    private LinearLayout list;
    private TextView headline;
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
        if (AdcoreScheduler.isDailySyncDue(this)) {
            AdcoreLogger.i(TAG, "Missed 00:30 daily pull detected; this SyncActivity run will refresh resources and update the daily marker.");
        }
        boolean hasCache = AdcoreDatabase.getInstance(this).hasCachedVideos();
        if (hasCache) {
            AdcoreLogger.i(TAG, "Cached videos already exist. Starting AdsActivity while sync continues in background.");
            AdcoreSyncService.startInitial(this, false);
            goTo(AdsActivity.class, true);
            return;
        }
        buildUi();
        registerProgressReceiver();
        AdcoreSyncService.startInitial(this, true);
    }

    @Override
    protected void onDestroy() {
        if (receiverRegistered) {
            unregisterReceiver(progressReceiver);
            receiverRegistered = false;
        }
        super.onDestroy();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(backgroundColor());
        root.setPadding(dp(48), dp(38), dp(48), dp(38));

        headline = label("Syncing resources", 26, Color.WHITE);
        headline.setGravity(Gravity.LEFT);
        root.addView(headline);

        ScrollView scrollView = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scrollView.addView(list);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.topMargin = dp(24);
        root.addView(scrollView, scrollParams);
        setContentView(root);
    }

    private void registerProgressReceiver() {
        IntentFilter filter = new IntentFilter(SyncProgressBroadcaster.ACTION_SYNC_PROGRESS);
        ContextCompat.registerReceiver(this, progressReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        receiverRegistered = true;
    }

    private void handleProgress(Intent intent) {
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
        row.status.setTextColor(error ? Color.rgb(255, 110, 110) : Color.rgb(185, 205, 225));
        row.progress.setProgress(progress);
        if (terminal) {
            headline.setText(success ? "Sync complete" : "Sync failed");
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
            container = new LinearLayout(SyncActivity.this);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setPadding(0, dp(12), 0, dp(12));
            title = label(titleValue, 18, Color.WHITE);
            status = label("", 14, Color.rgb(185, 205, 225));
            progress = new ProgressBar(SyncActivity.this, null, android.R.attr.progressBarStyleHorizontal);
            progress.setMax(100);
            progress.setProgress(0);
            container.addView(title);
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            statusParams.topMargin = dp(4);
            container.addView(status, statusParams);
            LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(8));
            progressParams.topMargin = dp(8);
            container.addView(progress, progressParams);
        }
    }
}
