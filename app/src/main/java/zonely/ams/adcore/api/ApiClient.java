package zonely.ams.adcore.api;

import android.content.Context;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import zonely.ams.adcore.AdcoreContext;
import zonely.ams.adcore.config.ApiConfig;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.model.LoginResult;
import zonely.ams.adcore.model.MappedResourcesResponse;

public class ApiClient {
    private static final String TAG = "ApiClient";
    private final Context appContext;

    public ApiClient(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public LoginResult login(String username, String password) throws ApiException {
        try {
            JSONObject payload = new JSONObject();
            payload.put("usernameOrEmail", username);
            payload.put("password", password);
            HttpResult result = jsonRequest("POST", ApiConfig.LOGIN_URL, payload.toString(), false);
            JSONObject root = new JSONObject(result.body);
            if (result.code != HttpURLConnection.HTTP_OK || !root.optBoolean("success", false)) {
                throw new ApiException(result.code, root.optString("message", "Login failed"));
            }
            JSONObject data = root.optJSONObject("data");
            if (data == null) {
                throw new ApiException(result.code, "Login response did not contain data.");
            }
            LoginResult loginResult = new LoginResult();
            loginResult.success = true;
            loginResult.message = root.optString("message", "Login successful");
            loginResult.accessToken = data.optString("accessToken", null);
            loginResult.refreshToken = data.optString("refreshToken", null);
            loginResult.tokenType = data.optString("tokenType", "Bearer");
            loginResult.expiresIn = data.optLong("expiresIn", 0L);
            loginResult.userId = data.optString("userId", null);
            loginResult.username = data.optString("username", username);
            loginResult.email = data.optString("email", null);
            JSONArray roles = data.optJSONArray("roles");
            if (roles != null) {
                for (int i = 0; i < roles.length(); i++) {
                    loginResult.roles.add(roles.optString(i));
                }
            }
            AdcoreLogger.i(TAG, "Login API successful for username=" + username);
            return loginResult;
        } catch (ApiException exception) {
            AdcoreLogger.w(TAG, "Login API failed for username=" + username + " httpCode=" + exception.getHttpCode() + " message=" + exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(-1, "Login failed: " + exception.getMessage(), exception);
        }
    }

    public MappedResourcesResponse getMappedResources(String deviceId) throws ApiException {
        String url = ApiConfig.GET_MAPPED_RESOURCES_URL + encode(deviceId);
        try {
            HttpResult result = jsonRequest("GET", url, null, true);
            JSONObject root = new JSONObject(result.body);
            if (result.code != HttpURLConnection.HTTP_OK || !root.optBoolean("success", false)) {
                throw new ApiException(result.code, root.optString("message", "getMappedResources failed"));
            }
            MappedResourcesResponse response = MappedResourcesResponse.fromJson(root);
            AdcoreLogger.i(TAG, "getMappedResources successful. deviceId=" + deviceId + " resourceCount=" + response.resources.size());
            return response;
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(-1, "getMappedResources failed: " + exception.getMessage(), exception);
        }
    }

    public void downloadResource(String resourceId, File targetFile, DownloadProgress progress) throws ApiException {
        String url = ApiConfig.DOWNLOAD_RESOURCE_URL + encode(resourceId);
        downloadFile(url, targetFile, true, progress);
    }

    public boolean downloadLatestApp(String appVersion, File targetFile, DownloadProgress progress) throws ApiException {
        String url = ApiConfig.DOWNLOAD_LATEST_APP_URL + "?appVersion=" + encode(appVersion);
        try {
            downloadFile(url, targetFile, true, progress);
            return true;
        } catch (ApiException exception) {
            if (exception.getHttpCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                AdcoreLogger.i(TAG, "Latest app check returned 404, current version is already latest.");
                return false;
            }
            throw exception;
        }
    }

    public void sendDeviceLogs(File zipFile, String deviceId, String type) throws ApiException {
        JSONObject request = new JSONObject();
        try {
            request.put("deviceId", deviceId);
            request.put("action", "PULL");
            request.put("type", type == null ? "ALL" : type);
            multipart(ApiConfig.SAVE_DEVICE_LOGS_URL, request.toString(), zipFile, "logFile", true);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(-1, "sendDeviceLogs failed: " + exception.getMessage(), exception);
        }
    }

    public void sendDeviceDb(File zipFile, String deviceId) throws ApiException {
        try {
            String url = ApiConfig.SAVE_DEVICE_DB_URL + encode(deviceId);
            multipart(url, "{\"deviceId\":\"" + escapeJson(deviceId) + "\"}", zipFile, "logFile", true);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(-1, "sendDeviceDb failed: " + exception.getMessage(), exception);
        }
    }

    public void sendDeviceData(String deviceId, JSONObject payload) throws ApiException {
        try {
            String url = ApiConfig.SAVE_DEVICE_DATA_URL + "/" + encode(deviceId);
            HttpResult result = jsonRequest("POST", url, payload.toString(), true);
            JSONObject root = new JSONObject(result.body);
            if (result.code != HttpURLConnection.HTTP_OK || !root.optBoolean("success", false)) {
                throw new ApiException(result.code, root.optString("message", "sendDeviceData failed"));
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(-1, "sendDeviceData failed: " + exception.getMessage(), exception);
        }
    }

    private HttpResult jsonRequest(String method, String urlValue, String payload, boolean auth) throws IOException, ApiException {
        HttpURLConnection connection = (HttpURLConnection) new URL(urlValue).openConnection();
        connection.setConnectTimeout(ApiConfig.CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(ApiConfig.READ_TIMEOUT_MS);
        connection.setRequestMethod(method);
        connection.setRequestProperty("Accept", "application/json");
        if (auth) {
            String authHeader = AdcoreContext.authorizationHeader();
            if (authHeader != null) {
                connection.setRequestProperty("Authorization", authHeader);
            }
        }
        if (payload != null) {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
        }
        int code = connection.getResponseCode();
        String body = readBody(connection, code);
        connection.disconnect();
        return new HttpResult(code, body);
    }

    private void downloadFile(String urlValue, File targetFile, boolean auth, DownloadProgress progress) throws ApiException {
        HttpURLConnection connection = null;
        File tempFile = new File(targetFile.getParentFile(), targetFile.getName() + ".download");
        try {
            connection = (HttpURLConnection) new URL(urlValue).openConnection();
            connection.setConnectTimeout(ApiConfig.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(ApiConfig.READ_TIMEOUT_MS);
            connection.setRequestMethod("GET");
            if (auth) {
                String authHeader = AdcoreContext.authorizationHeader();
                if (authHeader != null) {
                    connection.setRequestProperty("Authorization", authHeader);
                }
            }
            int code = connection.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) {
                String error = readBody(connection, code);
                throw new ApiException(code, error.isEmpty() ? "Download failed with HTTP " + code : error);
            }
            long total = 0;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                total = connection.getContentLengthLong();
            } else {
                // Fallback for older Android versions (API 23 and below)
                String contentLengthHeader = connection.getHeaderField("Content-Length");
                if (contentLengthHeader != null && !contentLengthHeader.isEmpty()) {
                    try {
                        total = Long.parseLong(contentLengthHeader);
                    } catch (NumberFormatException e) {
                        // Fallback to basic int if header parsing fails unexpectedly
                        total = connection.getContentLength();
                    }
                } else {
                    total = connection.getContentLength();
                }
            }
            File parent = targetFile.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                throw new IOException("Unable to create download directory: " + parent.getAbsolutePath());
            }
            long readBytes = 0L;
            byte[] buffer = new byte[64 * 1024];
            try (InputStream input = new BufferedInputStream(connection.getInputStream());
                 OutputStream output = new BufferedOutputStream(new FileOutputStream(tempFile))) {
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    readBytes += read;
                    if (progress != null) {
                        progress.onProgress(readBytes, total);
                    }
                }
            }
            if (targetFile.exists() && !targetFile.delete()) {
                throw new IOException("Unable to replace existing file: " + targetFile.getAbsolutePath());
            }
            if (!tempFile.renameTo(targetFile)) {
                throw new IOException("Unable to rename temp download to " + targetFile.getAbsolutePath());
            }
            if (progress != null) {
                progress.onProgress(readBytes, readBytes);
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(-1, "Download failed: " + exception.getMessage(), exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    private void multipart(String urlValue, String requestJson, File file, String filePartName, boolean auth) throws ApiException {
        String boundary = "AdcoreBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlValue).openConnection();
            connection.setConnectTimeout(ApiConfig.CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(ApiConfig.READ_TIMEOUT_MS);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            if (auth) {
                String authHeader = AdcoreContext.authorizationHeader();
                if (authHeader != null) {
                    connection.setRequestProperty("Authorization", authHeader);
                }
            }
            try (DataOutputStream output = new DataOutputStream(connection.getOutputStream())) {
                writeTextPart(output, boundary, "request", requestJson);
                writeFilePart(output, boundary, filePartName, file);
                output.writeBytes("--" + boundary + "--\r\n");
                output.flush();
            }
            int code = connection.getResponseCode();
            String body = readBody(connection, code);
            if (code != HttpURLConnection.HTTP_OK) {
                throw new ApiException(code, body.length() == 0 ? "Multipart upload failed with HTTP " + code : body);
            }
            if (body.trim().length() > 0) {
                JSONObject root = new JSONObject(body);
                if (!root.optBoolean("success", false)) {
                    throw new ApiException(code, root.optString("message", "Multipart upload failed"));
                }
            }
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(-1, "Multipart upload failed: " + exception.getMessage(), exception);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private void writeTextPart(DataOutputStream output, String boundary, String name, String value) throws IOException {
        output.writeBytes("--" + boundary + "\r\n");
        output.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        output.writeBytes("Content-Type: application/json; charset=utf-8\r\n\r\n");
        output.write(value.getBytes(StandardCharsets.UTF_8));
        output.writeBytes("\r\n");
    }

    private void writeFilePart(DataOutputStream output, String boundary, String name, File file) throws IOException {
        output.writeBytes("--" + boundary + "\r\n");
        output.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + file.getName() + "\"\r\n");
        output.writeBytes("Content-Type: application/zip\r\n\r\n");
        byte[] buffer = new byte[8192];
        try (FileInputStream input = new FileInputStream(file)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        }
        output.writeBytes("\r\n");
    }

    private String readBody(HttpURLConnection connection, int code) throws IOException {
        InputStream stream = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
        if (stream == null) {
            return "";
        }
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString("UTF-8");
        }
    }

    private String encode(String value) {
        try {
            return URLEncoder.encode(value == null ? "" : value, "UTF-8");
        } catch (Exception exception) {
            return "";
        }
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class HttpResult {
        final int code;
        final String body;

        HttpResult(int code, String body) {
            this.code = code;
            this.body = body == null ? "" : body;
        }
    }
}
