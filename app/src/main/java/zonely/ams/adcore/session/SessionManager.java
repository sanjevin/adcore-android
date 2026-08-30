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
import zonely.ams.adcore.util.NetworkUtils;
import zonely.ams.adcore.util.TimeUtils;

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
                boolean hasCache = db.hasCachedVideos();
                if (!NetworkUtils.hasActiveNetwork(context)) {
                    finishStartupWithLocalFallback(context, db, stored, credentials, hasCache,
                            AuthPolicy.INTERNET_UNAVAILABLE_MESSAGE);
                    return;
                }
                try {
                    AdcoreLogger.i(TAG, "Saved session found; validating token before startup.");
                    new ApiClient(context).ensureAuthenticatedSession();
                    AdcoreScheduler.scheduleImmediateAppUpdate(context);
                    finishStartup(SUCCESS, "Saved session ready");
                } catch (ApiException exception) {
                    if (ApiClient.isConnectivityFailure(exception)) {
                        finishStartupWithLocalFallback(context, db, stored, credentials, hasCache,
                                connectivityMessage(exception));
                        return;
                    }
                    finishStartup(FAILED, startupFailureMessage(exception));
                } catch (Exception exception) {
                    AdcoreLogger.e(TAG, "Unexpected session validation failure.", exception);
                    finishStartup(FAILED, AuthPolicy.SESSION_EXPIRED_MESSAGE);
                }
            }
        });
    }

    public static LoginResult loginAndPersist(Context context, String username, String password) {
        AdcoreDatabase db = AdcoreDatabase.getInstance(context);
        boolean hasCache = db.hasCachedVideos();
        if (!NetworkUtils.hasActiveNetwork(context)) {
            if (!hasCache) {
                return LoginResult.popupFailure(AuthPolicy.INTERNET_UNAVAILABLE_MESSAGE);
            }
            return loginWithStoredCredentials(context, db, username, password,
                    AuthPolicy.INTERNET_UNAVAILABLE_MESSAGE);
        }
        try {
            LoginResult result = new ApiClient(context).login(username, password);
            db.saveSession(result, username, password);
            db.markSuccessfulServerAuth("login");
            AdcoreContext.setLoginResult(result);
            return result;
        } catch (ApiException exception) {
            if (ApiClient.isConnectivityFailure(exception)) {
                String message = connectivityMessage(exception);
                if (!hasCache) {
                    return LoginResult.popupFailure(message);
                }
                return loginWithStoredCredentials(context, db, username, password, message);
            }
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

    private static void finishStartupWithLocalFallback(Context context, AdcoreDatabase db, LoginResult stored,
                                                       Credentials credentials, boolean hasCache, String message) {
        if (hasCache && credentials != null && credentials.isValid() && isWithinLocalLoginGrace(db)) {
            LoginResult local = stored == null ? LoginResult.localSuccess("Local login ready") : stored;
            local.success = true;
            local.localLogin = true;
            local.message = "Local login ready";
            AdcoreContext.setLoginResult(local);
            finishStartup(SUCCESS, "Local login ready");
            AdcoreLogger.i(TAG, "Startup allowed using local login fallback. reason=" + message);
            return;
        }
        if (hasCache && credentials != null && credentials.isValid() && hasLocalLoginGraceExpired(db)) {
            clearSession(context);
            AdcoreLogger.w(TAG, "Startup local login grace expired; session cleared. reason=" + message);
        } else {
            AdcoreContext.clear();
        }
        finishStartup(FAILED, message);
    }

    private static LoginResult loginWithStoredCredentials(Context context, AdcoreDatabase db, String username,
                                                         String password, String connectivityMessage) {
        Credentials stored = db.getStoredCredentials();
        if (stored == null || !stored.isValid()) {
            AdcoreLogger.w(TAG, "Local login unavailable because stored credentials are missing.");
            return LoginResult.popupFailure(connectivityMessage);
        }
        if (!stored.username.equals(username) || !stored.password.equals(password)) {
            AdcoreLogger.w(TAG, "Local login rejected because entered credentials do not match stored credentials.");
            return LoginResult.failure("Local username or password is incorrect.");
        }
        if (!isWithinLocalLoginGrace(db)) {
            if (hasLocalLoginGraceExpired(db)) {
                clearSession(context);
                AdcoreLogger.w(TAG, "Local login grace expired; session cleared.");
            }
            return LoginResult.popupFailure(connectivityMessage);
        }
        LoginResult local = db.getStoredSession();
        if (local == null) {
            local = LoginResult.localSuccess("Local login successful");
            local.username = stored.username;
        }
        local.success = true;
        local.localLogin = true;
        local.message = "Local login successful";
        AdcoreContext.setLoginResult(local);
        AdcoreLogger.i(TAG, "Local login successful. reason=" + connectivityMessage);
        return local;
    }

    private static boolean isWithinLocalLoginGrace(AdcoreDatabase db) {
        long lastServerAuth = db.getLastSuccessfulServerAuthAt();
        if (lastServerAuth <= 0L) {
            return false;
        }
        return TimeUtils.now() <= lastServerAuth + localLoginGraceMs(db);
    }

    private static boolean hasLocalLoginGraceExpired(AdcoreDatabase db) {
        long lastServerAuth = db.getLastSuccessfulServerAuthAt();
        return lastServerAuth > 0L && TimeUtils.now() > lastServerAuth + localLoginGraceMs(db);
    }

    private static long localLoginGraceMs(AdcoreDatabase db) {
        return db.getLocalLoginGraceDays() * 24L * 60L * 60L * 1000L;
    }

    private static String connectivityMessage(ApiException exception) {
        if (ApiClient.isNetworkUnavailable(exception)) {
            return AuthPolicy.INTERNET_UNAVAILABLE_MESSAGE;
        }
        return AuthPolicy.SERVER_NOT_REACHABLE_MESSAGE;
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
