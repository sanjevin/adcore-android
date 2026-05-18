package zonely.ams.adcore.api;

public class ApiException extends Exception {
    private final int httpCode;

    public ApiException(int httpCode, String message) {
        super(message);
        this.httpCode = httpCode;
    }

    public ApiException(int httpCode, String message, Throwable cause) {
        super(message, cause);
        this.httpCode = httpCode;
    }

    public int getHttpCode() {
        return httpCode;
    }
}
