package zonely.ams.adcore.activity;

import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
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
        LinearLayout root = baseRoot(Gravity.CENTER);
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        root.addView(spinner, new LinearLayout.LayoutParams(dp(72), dp(72)));
        TextView text = label("Logging in", 24, Color.WHITE);
        text.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        textParams.topMargin = dp(18);
        root.addView(text, textParams);
        setContentView(root);
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
        LinearLayout root = baseRoot(Gravity.CENTER);
        root.setPadding(dp(80), dp(40), dp(80), dp(40));

        TextView title = label("adcore", 34, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        usernameEdit = editText("Username or email", false);
        addWithTopMargin(root, usernameEdit, dp(30));
        usernameError = errorLabel();
        root.addView(usernameError);

        passwordEdit = editText("Password", true);
        addWithTopMargin(root, passwordEdit, dp(14));
        passwordError = errorLabel();
        root.addView(passwordError);

        loginButton = new Button(this);
        loginButton.setText("Login");
        loginButton.setOnClickListener(this);
        addWithTopMargin(root, loginButton, dp(22));

        progressRow = new LinearLayout(this);
        progressRow.setGravity(Gravity.CENTER);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        ProgressBar spinner = new ProgressBar(this);
        spinner.setIndeterminate(true);
        progressRow.addView(spinner, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView progressText = label("Logging in", 18, Color.WHITE);
        LinearLayout.LayoutParams progressTextParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        progressTextParams.leftMargin = dp(12);
        progressRow.addView(progressText, progressTextParams);
        progressRow.setVisibility(View.GONE);
        addWithTopMargin(root, progressRow, dp(22));

        loginMessage = label("", 16, Color.rgb(255, 110, 110));
        loginMessage.setGravity(Gravity.CENTER);
        addWithTopMargin(root, loginMessage, dp(14));
        if (message != null && message.trim().length() > 0) {
            loginMessage.setText(message);
        }
        setContentView(root);
    }

    private void submitLogin() {
        usernameError.setText("");
        passwordError.setText("");
        loginMessage.setText("");
        final String username = usernameEdit.getText().toString().trim();
        final String password = passwordEdit.getText().toString();
        boolean valid = true;
        if (username.length() == 0) {
            usernameError.setText("Username is required");
            valid = false;
        }
        if (password.length() == 0) {
            passwordError.setText("Password is required");
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
                            loginMessage.setText(result.message == null ? "Login failed" : result.message);
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

    private LinearLayout baseRoot(int gravity) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(gravity);
        root.setBackgroundColor(backgroundColor());
        return root;
    }

    private EditText editText(String hint, boolean password) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setSingleLine(true);
        editText.setTextColor(Color.WHITE);
        editText.setHintTextColor(Color.rgb(170, 185, 205));
        editText.setTextSize(18);
        editText.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        editText.setMinWidth(dp(420));
        return editText;
    }

    private TextView errorLabel() {
        TextView view = label("", 14, Color.rgb(255, 110, 110));
        view.setGravity(Gravity.LEFT);
        view.setMinHeight(dp(24));
        return view;
    }

    private void addWithTopMargin(LinearLayout root, View child, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        root.addView(child, params);
    }
}
