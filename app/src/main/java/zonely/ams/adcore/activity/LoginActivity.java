package zonely.ams.adcore.activity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import zonely.ams.adcore.R;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.model.LoginResult;
import zonely.ams.adcore.scheduler.AdcoreScheduler;
import zonely.ams.adcore.session.SessionManager;
import zonely.ams.adcore.util.AppExecutors;

public class LoginActivity extends BaseActivity {
    public static final String EXTRA_AUTO_LOGGING = "auto_logging";
    public static final String EXTRA_LOGIN_MESSAGE = "login_message";
    private static final String TAG = "LoginActivity";

    private EditText usernameEdit;
    private EditText passwordEdit;
    private TextView usernameError;
    private TextView passwordError;
    private TextView loginMessage;
    private Button loginButton;
    private LinearLayout progressRow;
    private View autoLoginContainer;
    private View loginFormContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        bindViews();
        if (getIntent().getBooleanExtra(EXTRA_AUTO_LOGGING, false)) {
            showAutoLogging();
            return;
        }
        showLoginForm(getIntent().getStringExtra(EXTRA_LOGIN_MESSAGE));
    }

    @Override
    protected void handleOnClick(View view) {
        if (view == loginButton) {
            submitLogin();
        }
    }

    private void showAutoLogging() {
        autoLoginContainer.setVisibility(View.VISIBLE);
        loginFormContainer.setVisibility(View.GONE);
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                final int state = SessionManager.awaitStartupLogin();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (state == SessionManager.SUCCESS) {
                            goTo(SyncActivity.class, true);
                        } else {
                            showLoginForm(SessionManager.getStartupMessage());
                        }
                    }
                });
            }
        });
    }

    private void showLoginForm(String message) {
        autoLoginContainer.setVisibility(View.GONE);
        loginFormContainer.setVisibility(View.VISIBLE);
        setLoading(false);
        usernameError.setText("");
        passwordError.setText("");
        loginMessage.setText("");
        if (message != null && message.trim().length() > 0) {
            loginMessage.setText(message);
        }
    }

    private void submitLogin() {
        usernameError.setText("");
        passwordError.setText("");
        loginMessage.setText("");
        final String username = usernameEdit.getText().toString().trim();
        final String password = passwordEdit.getText().toString();
        boolean valid = true;
        if (username.length() == 0) {
            usernameError.setText(R.string.username_required);
            valid = false;
        }
        if (password.length() == 0) {
            passwordError.setText(R.string.password_required);
            valid = false;
        }
        if (!valid) {
            return;
        }
        setLoading(true);
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                final LoginResult result = SessionManager.loginAndPersist(LoginActivity.this, username, password);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result.success) {
                            AdcoreLogger.i(TAG, "Login successful; scheduling app update check and opening SyncActivity.");
                            AdcoreScheduler.scheduleImmediateAppUpdate(LoginActivity.this);
                            goTo(SyncActivity.class, true);
                        } else {
                            setLoading(false);
                            loginMessage.setText(result.message == null ? getString(R.string.login_failed) : result.message);
                        }
                    }
                });
            }
        });
    }

    private void setLoading(boolean loading) {
        usernameEdit.setEnabled(!loading);
        passwordEdit.setEnabled(!loading);
        loginButton.setVisibility(loading ? View.GONE : View.VISIBLE);
        progressRow.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    private void bindViews() {
        autoLoginContainer = findViewById(R.id.auto_login_container);
        loginFormContainer = findViewById(R.id.login_form_container);
        usernameEdit = findViewById(R.id.username_edit);
        passwordEdit = findViewById(R.id.password_edit);
        usernameError = findViewById(R.id.username_error);
        passwordError = findViewById(R.id.password_error);
        loginMessage = findViewById(R.id.login_message);
        loginButton = findViewById(R.id.login_button);
        progressRow = findViewById(R.id.login_progress_row);
        loginButton.setOnClickListener(this);
    }
}
