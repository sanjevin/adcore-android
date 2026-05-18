package zonely.ams.adcore.sync;

import android.content.Context;
import android.content.Intent;

public final class SyncProgressBroadcaster {
    public static final String ACTION_SYNC_PROGRESS = "zonely.ams.adcore.ACTION_SYNC_PROGRESS";
    public static final String EXTRA_STEP_ID = "step_id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_PROGRESS = "progress";
    public static final String EXTRA_DONE = "done";
    public static final String EXTRA_ERROR = "error";
    public static final String EXTRA_TERMINAL = "terminal";
    public static final String EXTRA_SUCCESS = "success";

    private SyncProgressBroadcaster() {
    }

    public static void send(Context context, String stepId, String title, String status,
                            int progress, boolean done, boolean error) {
        Intent intent = new Intent(ACTION_SYNC_PROGRESS);
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_STEP_ID, stepId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_PROGRESS, Math.max(0, Math.min(100, progress)));
        intent.putExtra(EXTRA_DONE, done);
        intent.putExtra(EXTRA_ERROR, error);
        context.sendBroadcast(intent);
    }

    public static void terminal(Context context, boolean success, String status) {
        Intent intent = new Intent(ACTION_SYNC_PROGRESS);
        intent.setPackage(context.getPackageName());
        intent.putExtra(EXTRA_STEP_ID, "sync_terminal");
        intent.putExtra(EXTRA_TITLE, "Sync");
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_PROGRESS, success ? 100 : 0);
        intent.putExtra(EXTRA_DONE, true);
        intent.putExtra(EXTRA_ERROR, !success);
        intent.putExtra(EXTRA_TERMINAL, true);
        intent.putExtra(EXTRA_SUCCESS, success);
        context.sendBroadcast(intent);
    }
}
