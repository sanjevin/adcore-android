package zonely.ams.adcore.config;

public final class ApiConfig {
    public static final String BASE_URL = "https://api-test.adcorezonely.dpdns.org/adcore/api";
//    public static final String BASE_URL = "http://192.168.1.15:8081/adcore/api";
    public static final String LOGIN_URL = BASE_URL + "/v1/auth/login";
    public static final String GET_MAPPED_RESOURCES_URL = BASE_URL + "/v1/deviceManagement/getMappedResources/";
    public static final String DOWNLOAD_RESOURCE_URL = BASE_URL + "/v1/resources/download/";
    public static final String SAVE_DEVICE_LOGS_URL = BASE_URL + "/v1/deviceManagement/saveDeviceLogs";
    public static final String SAVE_DEVICE_DB_URL = BASE_URL + "/v1/deviceManagement/saveDeviceDB/";
    public static final String SAVE_DEVICE_DATA_URL = BASE_URL + "/v1/deviceManagement/saveDeviceData";
    public static final String DOWNLOAD_LATEST_APP_URL = BASE_URL + "/v1/deviceManagement/downloadLatestApp";

    public static final int CONNECT_TIMEOUT_MS = 30000;
    public static final int READ_TIMEOUT_MS = 120000;

    private ApiConfig() {
    }
}
