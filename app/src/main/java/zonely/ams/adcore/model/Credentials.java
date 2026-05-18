package zonely.ams.adcore.model;

public class Credentials {
    public final String username;
    public final String password;

    public Credentials(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public boolean isValid() {
        return username != null && username.trim().length() > 0
                && password != null && password.length() > 0;
    }
}
