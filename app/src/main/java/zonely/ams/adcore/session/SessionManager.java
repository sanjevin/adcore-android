package zonely.ams.adcore.session;

import android.content.Context;

import zonely.ams.adcore.AdcoreContext;
import zonely.ams.adcore.api.ApiClient;
import zonely.ams.adcore.api.ApiException;
import zonely.ams.adcore.data.AdcoreDatabase;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.model.Credentials;
import zonely.ams.adcore.model.LoginResult;
import zonely.ams.adcore.scheduler.AdcoreScheduler;
import zonely.ams.adcore.util.AppExecutors;

public final class SessionManager {
    public static final int IDLE = 0;
    public static final int IN_PROGRESS = 1;
    public static final int SUCCESS = 2;
    public static final int NO_CREDENTIALS = 3;
    public static final int FAILED = 4;

    private static final String TAG = "SessionManager";
    private static final Object LOCK = new Object();
    private static int startupState = IDLE;
    private static String startupMessage = "";

    private SessionManager() {
    }

    public static void loadStoredSession(Context context) {
        LoginResult stored = AdcoreDatabase.getInstance(context).getStoredSession();
        if (stored != null && stored.accessToken != null) {
            AdcoreContext.setLoginResult(stored);
            AdcoreLogger.i(TAG, "Stored access token loaded into AppContext.");
        }
    }

    public static void startStoredCredentialLogin(final Context context) {
        synchronized (LOCK) {
            if (startupState == IN_PROGRESS) {
                return;
            }
            startupState = IN_PROGRESS;
            startupMessage = "Checking stored credentials";
        }
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                AdcoreDatabase db = AdcoreDatabase.getInstance(context);
                Credentials credentials = db.getCredentials();
                if (credentials == null || !credentials.isValid()) {
                    finishStartup(NO_CREDENTIALS, "No stored credentials found");
                    return;
                }
                try {
                    AdcoreLogger.i(TAG, "Stored credentials found; attempting implicit login.");
                    LoginResult result = new ApiClient(context).login(credentials.username, credentials.password);
                    db.saveSession(result);
                    AdcoreContext.setLoginResult(result);
                    AdcoreScheduler.scheduleImmediateAppUpdate(context);
                    finishStartup(SUCCESS, "Implicit login successful");
                } catch (ApiException exception) {
                    finishStartup(FAILED, exception.getMessage());
                } catch (Exception exception) {
                    AdcoreLogger.e(TAG, "Unexpected implicit login failure.", exception);
                    finishStartup(FAILED, exception.getMessage());
                }
            }
        });
    }

    public static LoginResult loginAndPersist(Context context, String username, String password) {
        try {
            LoginResult result = new ApiClient(context).login(username, password);
            AdcoreDatabase.getInstance(context).saveCredentials(username, password, result);
            AdcoreContext.setLoginResult(result);
            return result;
        } catch (ApiException exception) {
            return LoginResult.failure(exception.getMessage());
        } catch (Exception exception) {
            AdcoreLogger.e(TAG, "Login failed unexpectedly.", exception);
            return LoginResult.failure(exception.getMessage());
        }
    }

    public static int getStartupState() {
        synchronized (LOCK) {
            return startupState;
        }
    }

    public static String getStartupMessage() {
        synchronized (LOCK) {
            return startupMessage;
        }
    }

    public static int awaitStartupLogin() {
        synchronized (LOCK) {
            while (startupState == IN_PROGRESS) {
                try {
                    LOCK.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    startupState = FAILED;
                    startupMessage = "Startup login interrupted";
                    break;
                }
            }
            return startupState;
        }
    }

    public static void resetStartupState() {
        synchronized (LOCK) {
            startupState = IDLE;
            startupMessage = "";
            LOCK.notifyAll();
        }
    }

    private static void finishStartup(int state, String message) {
        synchronized (LOCK) {
            startupState = state;
            startupMessage = message == null ? "" : message;
            LOCK.notifyAll();
        }
        AdcoreLogger.i(TAG, "Startup login state=" + state + " message=" + message);
    }
}
