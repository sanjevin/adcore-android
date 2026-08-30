package zonely.ams.adcore.model;

import java.util.ArrayList;
import java.util.List;

public class LoginResult {
    public boolean success;
    public String message;
    public String code;
    public String accessToken;
    public String refreshToken;
    public String tokenType;
    public long expiresIn;
    public long accessTokenExpiresAt;
    public String userId;
    public String username;
    public String email;
    public boolean accountLocked;
    public boolean localLogin;
    public boolean popupMessage;
    public final List<String> roles = new ArrayList<>();

    public static LoginResult failure(String message) {
        LoginResult result = new LoginResult();
        result.success = false;
        result.message = message;
        return result;
    }

    public static LoginResult popupFailure(String message) {
        LoginResult result = failure(message);
        result.popupMessage = true;
        return result;
    }

    public static LoginResult localSuccess(String message) {
        LoginResult result = new LoginResult();
        result.success = true;
        result.localLogin = true;
        result.message = message;
        return result;
    }

    public static LoginResult locked(String message) {
        LoginResult result = failure(message);
        result.code = "ACCOUNT_LOCKED";
        result.accountLocked = true;
        return result;
    }
}
