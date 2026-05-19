package zonely.ams.adcore.config;

public final class AppConstants {
    public static final String APP_NAME = "Adcore";
    public static final String DB_NAME = "adcore.db";
    public static final int DB_VERSION = 1;

    public static final String LOG_DIR = "logs";
    public static final String EXPORT_DIR = "exports";
    public static final String RESOURCE_DIR = "resources";
    public static final String UPDATE_DIR = "updates";

    public static final long SPLASH_DURATION_MS = 5000L;
    public static final long MAX_LOG_FILE_BYTES = 10L * 1024L * 1024L;
    public static final int MAX_LOG_FILES = 10;

    public static final String MARKER_LAST_DAILY_SYNC_SUCCESS = "last_daily_sync_success_at";
    public static final String MARKER_LAST_AUDIT_SENT_DATE = "last_audit_sent_date";
    public static final String MARKER_LAST_UPDATE_CHECK_DATE = "last_update_check_date";
    public static final String CONFIG_API_RETRY_DELAY_SEC = "api_retry_delay_sec";
    public static final String CONFIG_API_RETRY_MAX = "api_retry_max";
    public static final String CONFIG_BACKGROUND_RETRY_DELAY_SEC = "background_retry_delay_sec";
    public static final String CONFIG_BACKGROUND_RETRY_MAX = "background_retry_max";
    public static final String CONFIG_DOWNLOAD_THREADS = "download_threads";

    private AppConstants() {
    }
}
