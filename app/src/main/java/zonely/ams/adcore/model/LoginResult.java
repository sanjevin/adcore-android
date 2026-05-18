package zonely.ams.adcore.model;

import java.util.ArrayList;
import java.util.List;

public class LoginResult {
    public boolean success;
    public String message;
    public String accessToken;
    public String refreshToken;
    public String tokenType;
    public long expiresIn;
    public String userId;
    public String username;
    public String email;
    public final List<String> roles = new ArrayList<>();

    public static LoginResult failure(String message) {
        LoginResult result = new LoginResult();
        result.success = false;
        result.message = message;
        return result;
    }
}
