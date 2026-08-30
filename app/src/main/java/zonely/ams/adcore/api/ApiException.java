package zonely.ams.adcore.api;

public class ApiException extends Exception {
    public static final int HTTP_NETWORK_UNAVAILABLE = -100;
    public static final int HTTP_SERVER_UNREACHABLE = -101;
    public static final String CODE_NETWORK_UNAVAILABLE = "NETWORK_UNAVAILABLE";
    public static final String CODE_SERVER_UNREACHABLE = "SERVER_UNREACHABLE";

    private final int httpCode;
    private final String apiCode;

    public ApiException(int httpCode, String message) {
        this(httpCode, null, message, null);
    }

    public ApiException(int httpCode, String apiCode, String message) {
        this(httpCode, apiCode, message, null);
    }

    public ApiException(int httpCode, String message, Throwable cause) {
        this(httpCode, null, message, cause);
    }

    public ApiException(int httpCode, String apiCode, String message, Throwable cause) {
        super(message, cause);
        this.httpCode = httpCode;
        this.apiCode = apiCode;
    }

    public int getHttpCode() {
        return httpCode;
    }

    public String getApiCode() {
        return apiCode;
    }
}
