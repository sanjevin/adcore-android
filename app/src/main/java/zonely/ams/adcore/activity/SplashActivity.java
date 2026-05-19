package zonely.ams.adcore.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import zonely.ams.adcore.R;
import zonely.ams.adcore.config.AppConstants;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.session.SessionManager;

public class SplashActivity extends BaseActivity {
    private static final String TAG = "SplashActivity";
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SessionManager.startStoredCredentialLogin(this);
        setContentView(R.layout.activity_splash);

        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                continueAfterSplash();
            }
        }, AppConstants.SPLASH_DURATION_MS);
    }

    private void continueAfterSplash() {
        int state = SessionManager.getStartupState();
        AdcoreLogger.i(TAG, "Splash complete. startupState=" + state + " message=" + SessionManager.getStartupMessage());
        if (state == SessionManager.SUCCESS) {
            goTo(SyncActivity.class, true);
            return;
        }
        Intent intent = new Intent(this, LoginActivity.class);
        if (state == SessionManager.IN_PROGRESS) {
            intent.putExtra(LoginActivity.EXTRA_AUTO_LOGGING, true);
        } else if (state == SessionManager.FAILED) {
            intent.putExtra(LoginActivity.EXTRA_LOGIN_MESSAGE, SessionManager.getStartupMessage());
        }
        startActivity(intent);
        finish();
    }
}
