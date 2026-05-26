package zonely.ams.adcore;

import zonely.ams.adcore.model.LoginResult;

public final class AdcoreContext {
    private static volatile String accessToken;
    private static volatile String refreshToken;
    private static volatile String tokenType = "Bearer";
    private static volatile long accessTokenExpiresAt;
    private static volatile String userId;
    private static volatile String username;
    private static volatile String email;

    private AdcoreContext() {
    }

    public static void setLoginResult(LoginResult result) {
        if (result == null) {
            return;
        }
        accessToken = result.accessToken;
        refreshToken = result.refreshToken;
        tokenType = result.tokenType == null ? "Bearer" : result.tokenType;
        accessTokenExpiresAt = result.accessTokenExpiresAt;
        userId = result.userId;
        username = result.username;
        email = result.email;
    }

    public static void clear() {
        accessToken = null;
        refreshToken = null;
        tokenType = "Bearer";
        accessTokenExpiresAt = 0L;
        userId = null;
        username = null;
        email = null;
    }

    public static boolean hasAccessToken() {
        return accessToken != null && accessToken.trim().length() > 0;
    }

    public static String authorizationHeader() {
        if (!hasAccessToken()) {
            return null;
        }
        return (tokenType == null ? "Bearer" : tokenType) + " " + accessToken;
    }

    public static String getUserId() {
        return userId;
    }

    public static String getUsername() {
        return username;
    }

    public static String getEmail() {
        return email;
    }

    public static String getRefreshToken() {
        return refreshToken;
    }

    public static long getAccessTokenExpiresAt() {
        return accessTokenExpiresAt;
    }
}
