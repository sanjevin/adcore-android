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
    public static final int NO_SESSION = 3;
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
            AdcoreLogger.i(TAG, "Stored token session loaded into AppContext.");
        }
    }

    public static void startStoredSessionRefresh(final Context context) {
        synchronized (LOCK) {
            if (startupState == IN_PROGRESS) {
                return;
            }
            startupState = IN_PROGRESS;
            startupMessage = "Checking saved session";
        }
        AppExecutors.io().execute(new Runnable() {
            @Override
            public void run() {
                AdcoreDatabase db = AdcoreDatabase.getInstance(context);
                LoginResult stored = db.getStoredSession();
                Credentials credentials = db.getStoredCredentials();
                if ((stored == null || stored.refreshToken == null || stored.refreshToken.trim().length() == 0)
                        && (credentials == null || !credentials.isValid())) {
                    AdcoreContext.clear();
                    finishStartup(NO_SESSION, "No saved session found");
                    return;
                }
                try {
                    AdcoreLogger.i(TAG, "Saved session found; validating token before startup.");
                    new ApiClient(context).ensureAuthenticatedSession();
                    AdcoreScheduler.scheduleImmediateAppUpdate(context);
                    finishStartup(SUCCESS, "Saved session ready");
                } catch (ApiException exception) {
                    finishStartup(FAILED, startupFailureMessage(exception));
                } catch (Exception exception) {
                    AdcoreLogger.e(TAG, "Unexpected session validation failure.", exception);
                    finishStartup(FAILED, AuthPolicy.SESSION_EXPIRED_MESSAGE);
                }
            }
        });
    }

    public static LoginResult loginAndPersist(Context context, String username, String password) {
        try {
            LoginResult result = new ApiClient(context).login(username, password);
            AdcoreDatabase.getInstance(context).saveSession(result, username, password);
            AdcoreContext.setLoginResult(result);
            return result;
        } catch (ApiException exception) {
            if (isAccountLocked(exception)) {
                AdcoreLogger.w(TAG, "Login rejected because account is locked. username=" + username);
                return LoginResult.locked(AuthPolicy.LOCKED_ACCOUNT_MESSAGE);
            }
            return LoginResult.failure(exception.getMessage());
        } catch (Exception exception) {
            AdcoreLogger.e(TAG, "Login failed unexpectedly.", exception);
            return LoginResult.failure(exception.getMessage());
        }
    }

    public static void logout(Context context) {
        String refreshToken = AdcoreContext.getRefreshToken();
        if (refreshToken == null || refreshToken.trim().length() == 0) {
            refreshToken = AdcoreDatabase.getInstance(context).getStoredRefreshToken();
        }
        try {
            new ApiClient(context).logout(refreshToken);
        } catch (Exception exception) {
            AdcoreLogger.w(TAG, "Logout API failed; local session will still be cleared.", exception);
        } finally {
            clearSession(context);
        }
    }

    public static void clearSession(Context context) {
        AdcoreDatabase.getInstance(context).clearSession();
        AdcoreContext.clear();
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

    private static boolean isAccountLocked(ApiException exception) {
        if (exception == null) {
            return false;
        }
        return AuthPolicy.isAccountLocked(exception.getApiCode(), exception.getMessage());
    }

    private static String startupFailureMessage(ApiException exception) {
        if (isAccountLocked(exception)) {
            return AuthPolicy.LOCKED_ACCOUNT_MESSAGE;
        }
        String message = exception == null ? null : exception.getMessage();
        return message == null || message.trim().length() == 0
                ? AuthPolicy.SESSION_EXPIRED_MESSAGE
                : message;
    }
}
