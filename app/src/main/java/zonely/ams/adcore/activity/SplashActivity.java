package zonely.ams.adcore.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(backgroundColor());
        root.setPadding(dp(40), dp(40), dp(40), dp(40));

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.mipmap.ic_launcher);
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(160), dp(160));
        root.addView(logo, logoParams);

        TextView name = label("adcore", 34, Color.WHITE);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = dp(18);
        root.addView(name, nameParams);
        setContentView(root);

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
