package zonely.ams.adcore.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;

import androidx.core.content.ContextCompat;

import zonely.ams.adcore.R;
import zonely.ams.adcore.install.AppInstaller;
import zonely.ams.adcore.install.InstallState;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.util.AppExecutors;

public class InstallPromptActivity extends BaseActivity {
    public static final String ACTION_INSTALL_RESULT = "zonely.ams.adcore.action.INSTALL_RESULT";
    public static final String EXTRA_INSTALL_STATUS = "install_status";
    public static final String EXTRA_INSTALL_MESSAGE = "install_message";
    private static final String TAG = "InstallPromptActivity";
    private static final String EXTRA_APK_PATH = "apk_path";
    private static final String STATE_INSTALL_STARTED = "install_started";
    private static final int REQUEST_INSTALL_APK = 7001;
    private static final long FINISH_AFTER_RESULT_MS = 1800L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView statusText;
    private ProgressBar spinner;
    private File apkFile;
    private boolean installStarted;
    private boolean resultReceiverRegistered;

    private final BroadcastReceiver installResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int status = intent.getIntExtra(EXTRA_INSTALL_STATUS, PackageInstaller.STATUS_FAILURE);
            String message = intent.getStringExtra(EXTRA_INSTALL_MESSAGE);
            handlePackageInstallerResult(status, message);
        }
    };

    public static boolean start(Context context, File apkFile) {
        if (apkFile == null || !apkFile.exists() || apkFile.length() == 0L) {
            AdcoreLogger.e(TAG, "Install prompt not started because APK is missing or empty.");
            return false;
        }
        Context appContext = context.getApplicationContext();
        InstallState.markPending(appContext, apkFile);
        Intent intent = new Intent(appContext, InstallPromptActivity.class);
        intent.putExtra(EXTRA_APK_PATH, apkFile.getAbsolutePath());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        try {
            appContext.startActivity(intent);
            AdcoreLogger.i(TAG, "Install prompt started. apk=" + apkFile.getAbsolutePath());
            return true;
        } catch (Exception exception) {
            InstallState.clear(appContext);
            AdcoreLogger.e(TAG, "Unable to start install prompt activity.", exception);
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_install_prompt);
        statusText = findViewById(R.id.install_prompt_status);
        spinner = findViewById(R.id.install_prompt_spinner);
        registerInstallResultReceiver();
        installStarted = savedInstanceState != null
                && savedInstanceState.getBoolean(STATE_INSTALL_STARTED, false);
        resolveApkFile(getIntent());
        if (!isApkReady()) {
            showTerminalMessage(R.string.update_install_missing_apk, true);
            InstallState.clear(this);
            finishAfterResult();
            return;
        }
        InstallState.markPending(this, apkFile);
        if (!installStarted) {
            startInstall();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        resolveApkFile(intent);
        if (isApkReady()) {
            InstallState.markPending(this, apkFile);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerInstallResultReceiver();
    }

    @Override
    protected void onStop() {
        unregisterInstallResultReceiver();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(STATE_INSTALL_STARTED, installStarted);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        if (InstallState.isPending(this)) {
            AdcoreLogger.i(TAG, "Back press consumed while update install is pending.");
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_INSTALL_APK) {
            return;
        }
        InstallState.clear(this);
        if (resultCode == RESULT_OK) {
            showTerminalMessage(R.string.update_install_started, false);
            AdcoreLogger.i(TAG, "Interactive APK install completed with RESULT_OK.");
        } else {
            showTerminalMessage(R.string.update_install_cancelled, false);
            AdcoreLogger.w(TAG, "Interactive APK install was cancelled or declined. resultCode=" + resultCode);
        }
        finishAfterResult();
    }

    private void startInstall() {
        installStarted = true;
        final AppInstaller installer = new AppInstaller(this);
        if (installer.isDeviceOwner()) {
            statusText.setText(R.string.update_install_silent_running);
            AppExecutors.io().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        installer.installSilently(apkFile);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                statusText.setText(R.string.update_install_started);
                            }
                        });
                    } catch (final Exception exception) {
                        AdcoreLogger.e(TAG, "Silent install failed before commit.", exception);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                InstallState.clear(InstallPromptActivity.this);
                                showTerminalMessage(R.string.update_install_failed, true);
                                finishAfterResult();
                            }
                        });
                    }
                }
            });
            return;
        }

        try {
            statusText.setText(R.string.update_install_permission_waiting);
            installer.launchInstallerUi(this, apkFile, REQUEST_INSTALL_APK);
        } catch (Exception exception) {
            InstallState.clear(this);
            showTerminalMessage(R.string.update_install_failed, true);
            AdcoreLogger.e(TAG, "Interactive installer launch failed.", exception);
            finishAfterResult();
        }
    }

    private void resolveApkFile(Intent intent) {
        String path = intent == null ? null : intent.getStringExtra(EXTRA_APK_PATH);
        if (path == null || path.trim().length() == 0) {
            File pending = InstallState.getPendingApk(this);
            apkFile = pending;
            return;
        }
        apkFile = new File(path);
    }

    private boolean isApkReady() {
        return apkFile != null && apkFile.exists() && apkFile.length() > 0L;
    }

    private void handlePackageInstallerResult(int status, String message) {
        InstallState.clear(this);
        if (status == PackageInstaller.STATUS_SUCCESS) {
            statusText.setText(R.string.update_install_started);
            AdcoreLogger.i(TAG, "PackageInstaller reported success for update install.");
            finishAfterResult();
            return;
        }
        showTerminalMessage(R.string.update_install_failed, true);
        AdcoreLogger.e(TAG, "PackageInstaller reported install failure. status=" + status + " message=" + message);
        finishAfterResult();
    }

    private void showTerminalMessage(int messageResId, boolean error) {
        spinner.setVisibility(View.GONE);
        statusText.setText(messageResId);
        statusText.setTextColor(ContextCompat.getColor(this,
                error ? R.color.adcore_error : R.color.adcore_text_secondary));
    }

    private void finishAfterResult() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, FINISH_AFTER_RESULT_MS);
    }

    private void registerInstallResultReceiver() {
        if (resultReceiverRegistered) {
            return;
        }
        IntentFilter filter = new IntentFilter(ACTION_INSTALL_RESULT);
        ContextCompat.registerReceiver(this, installResultReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
        resultReceiverRegistered = true;
    }

    private void unregisterInstallResultReceiver() {
        if (resultReceiverRegistered) {
            unregisterReceiver(installResultReceiver);
            resultReceiverRegistered = false;
        }
    }
}
