package zonely.ams.adcore.session;

import org.junit.Test;

import java.net.HttpURLConnection;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AuthPolicyTest {
    @Test
    public void isAccountLocked_acceptsStableCode() {
        assertTrue(AuthPolicy.isAccountLocked("ACCOUNT_LOCKED", "Authentication failed"));
    }

    @Test
    public void isAccountLocked_acceptsBackendLockedMessages() {
        assertTrue(AuthPolicy.isAccountLocked(null,
                "Authentication failed: Account locked after too many failed login attempts"));
        assertTrue(AuthPolicy.isAccountLocked(null,
                "Authentication failed: Account is locked. Please contact support."));
    }

    @Test
    public void isAccountLocked_rejectsGeneralAuthenticationFailure() {
        assertFalse(AuthPolicy.isAccountLocked(null, "Authentication failed: Bad credentials"));
    }

    @Test
    public void isTokenRejected_matchesAuthenticationHttpFailuresOnly() {
        assertTrue(AuthPolicy.isTokenRejected(HttpURLConnection.HTTP_BAD_REQUEST));
        assertTrue(AuthPolicy.isTokenRejected(HttpURLConnection.HTTP_UNAUTHORIZED));
        assertTrue(AuthPolicy.isTokenRejected(HttpURLConnection.HTTP_FORBIDDEN));
        assertFalse(AuthPolicy.isTokenRejected(HttpURLConnection.HTTP_INTERNAL_ERROR));
    }

    @Test
    public void hasUsableAccessToken_requiresTokenBeyondRefreshMargin() {
        long now = 1_000L;
        long margin = 300L;

        assertTrue(AuthPolicy.hasUsableAccessToken(true, 1_301L, now, margin));
        assertFalse(AuthPolicy.hasUsableAccessToken(true, 1_300L, now, margin));
        assertFalse(AuthPolicy.hasUsableAccessToken(false, 2_000L, now, margin));
    }
}
