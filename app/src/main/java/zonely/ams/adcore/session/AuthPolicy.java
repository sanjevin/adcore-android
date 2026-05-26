package zonely.ams.adcore.session;

import java.net.HttpURLConnection;
import java.util.Locale;

public final class AuthPolicy {
    public static final String ACCOUNT_LOCKED_CODE = "ACCOUNT_LOCKED";
    public static final String LOCKED_ACCOUNT_MESSAGE = "Account is locked. Please contact support.";
    public static final String SESSION_EXPIRED_MESSAGE = "Session expired. Please login again.";

    private AuthPolicy() {
    }

    public static boolean hasUsableAccessToken(boolean hasAccessToken, long accessTokenExpiresAt,
                                               long now, long refreshMarginMs) {
        return hasAccessToken && accessTokenExpiresAt > now + refreshMarginMs;
    }

    public static boolean isAccountLocked(String apiCode, String message) {
        if (ACCOUNT_LOCKED_CODE.equalsIgnoreCase(apiCode)) {
            return true;
        }
        return message != null && message.toLowerCase(Locale.ROOT).contains("locked");
    }

    public static boolean isTokenRejected(int httpCode) {
        return httpCode == HttpURLConnection.HTTP_BAD_REQUEST
                || httpCode == HttpURLConnection.HTTP_UNAUTHORIZED
                || httpCode == HttpURLConnection.HTTP_FORBIDDEN;
    }
}
