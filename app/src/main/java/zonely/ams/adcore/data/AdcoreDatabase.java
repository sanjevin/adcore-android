package zonely.ams.adcore.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import zonely.ams.adcore.config.AppConstants;
import zonely.ams.adcore.logging.AdcoreLogger;
import zonely.ams.adcore.model.Credentials;
import zonely.ams.adcore.model.LoginResult;
import zonely.ams.adcore.model.NodeItem;
import zonely.ams.adcore.model.ResourceItem;
import zonely.ams.adcore.model.RetryConfig;
import zonely.ams.adcore.util.TimeUtils;

public class AdcoreDatabase extends SQLiteOpenHelper {
    private static final String TAG = "AdcoreDatabase";
    private static volatile AdcoreDatabase instance;
    private final Context appContext;

    private AdcoreDatabase(Context context) {
        super(context.getApplicationContext(), AppConstants.DB_NAME, null, AppConstants.DB_VERSION);
        this.appContext = context.getApplicationContext();
    }

    public static AdcoreDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AdcoreDatabase.class) {
                if (instance == null) {
                    instance = new AdcoreDatabase(context);
                }
            }
        }
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createAuthSessionTable(db);
        db.execSQL("CREATE TABLE nodes (" +
                "id TEXT PRIMARY KEY," +
                "user_id TEXT," +
                "node_name TEXT," +
                "type_key TEXT," +
                "device_id TEXT," +
                "latitude REAL," +
                "longitude REAL," +
                "address TEXT," +
                "status TEXT," +
                "last_ping_at TEXT," +
                "created_at TEXT," +
                "updated_at TEXT)");
        db.execSQL("CREATE TABLE resources (" +
                "id TEXT PRIMARY KEY," +
                "user_id TEXT," +
                "file_name TEXT," +
                "file_uri TEXT," +
                "media_type TEXT," +
                "file_size_bytes INTEGER," +
                "checksum TEXT," +
                "duration_seconds INTEGER," +
                "type_key TEXT," +
                "status TEXT," +
                "display_order INTEGER," +
                "start_date TEXT," +
                "end_date TEXT," +
                "display_count INTEGER," +
                "created_at TEXT," +
                "updated_at TEXT," +
                "local_cache_path TEXT," +
                "last_downloaded_at INTEGER)");
        db.execSQL("CREATE INDEX idx_resources_order ON resources(display_order, created_at)");
        db.execSQL("CREATE TABLE playback_counts (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "play_date TEXT NOT NULL," +
                "resource_id TEXT NOT NULL," +
                "play_count INTEGER NOT NULL DEFAULT 0," +
                "total_play_seconds INTEGER NOT NULL DEFAULT 0," +
                "UNIQUE(play_date, resource_id))");
        db.execSQL("CREATE TABLE sync_runs (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "sync_type TEXT NOT NULL," +
                "started_at INTEGER NOT NULL," +
                "completed_at INTEGER," +
                "success INTEGER NOT NULL DEFAULT 0," +
                "total_time_sec INTEGER NOT NULL DEFAULT 0," +
                "details TEXT)");
        db.execSQL("CREATE TABLE api_metrics (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "sync_run_id INTEGER," +
                "api_name TEXT NOT NULL," +
                "resource_id TEXT," +
                "started_at INTEGER NOT NULL," +
                "completed_at INTEGER NOT NULL," +
                "duration_ms INTEGER NOT NULL," +
                "retries INTEGER NOT NULL," +
                "success INTEGER NOT NULL," +
                "message TEXT)");
        db.execSQL("CREATE TABLE config (" +
                "config_key TEXT PRIMARY KEY," +
                "config_value TEXT NOT NULL)");
        db.execSQL("CREATE TABLE markers (" +
                "marker_key TEXT PRIMARY KEY," +
                "marker_value TEXT)");
        db.execSQL("CREATE TABLE uptime_sessions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "session_type TEXT NOT NULL," +
                "started_at INTEGER NOT NULL," +
                "ended_at INTEGER)");
        db.execSQL("CREATE TABLE sent_error_logs (" +
                "signature TEXT PRIMARY KEY," +
                "sent_at INTEGER NOT NULL)");
        insertDefaultConfig(db);
        AdcoreLogger.i(TAG, "Database created with version=" + AppConstants.DB_VERSION);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        if (!db.isReadOnly()) {
            createAuthSessionTable(db);
            ensureAuthSessionColumns(db);
            migrateAndRemoveLegacyCredentialsTable(db);
            insertDefaultConfig(db);
        }
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        AdcoreLogger.w(TAG, "Database upgrade requested from " + oldVersion + " to " + newVersion + ". Fresh schema is used for this initial release.");
        db.execSQL("DROP TABLE IF EXISTS credentials");
        db.execSQL("DROP TABLE IF EXISTS auth_sessions");
        db.execSQL("DROP TABLE IF EXISTS nodes");
        db.execSQL("DROP TABLE IF EXISTS resources");
        db.execSQL("DROP TABLE IF EXISTS playback_counts");
        db.execSQL("DROP TABLE IF EXISTS sync_runs");
        db.execSQL("DROP TABLE IF EXISTS api_metrics");
        db.execSQL("DROP TABLE IF EXISTS config");
        db.execSQL("DROP TABLE IF EXISTS markers");
        db.execSQL("DROP TABLE IF EXISTS uptime_sessions");
        db.execSQL("DROP TABLE IF EXISTS sent_error_logs");
        onCreate(db);
    }

    public synchronized LoginResult getStoredSession() {
        Cursor cursor = getReadableDatabase().query("auth_sessions",
                null, "id = 1", null, null, null, null);
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            LoginResult result = new LoginResult();
            result.success = cursorValue(cursor, "access_token") != null;
            result.accessToken = cursorValue(cursor, "access_token");
            result.refreshToken = cursorValue(cursor, "refresh_token");
            result.tokenType = cursorValue(cursor, "token_type");
            result.accessTokenExpiresAt = cursor.isNull(cursor.getColumnIndex("expires_at"))
                    ? 0L : cursor.getLong(cursor.getColumnIndex("expires_at"));
            result.userId = cursorValue(cursor, "user_id");
            result.username = cursorValue(cursor, "username");
            result.email = cursorValue(cursor, "email");
            result.message = "Stored session loaded";
            return result;
        } finally {
            cursor.close();
        }
    }

    public synchronized String getStoredRefreshToken() {
        Cursor cursor = getReadableDatabase().query("auth_sessions",
                new String[]{"refresh_token"}, "id = 1", null, null, null, null);
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    public synchronized Credentials getStoredCredentials() {
        Cursor cursor = getReadableDatabase().query("auth_sessions",
                new String[]{"username", "password", "credential_recovery_blocked"},
                "id = 1", null, null, null, null);
        try {
            if (!cursor.moveToFirst() || cursor.getInt(2) == 1) {
                return null;
            }
            Credentials credentials = new Credentials(cursor.getString(0), cursor.getString(1));
            return credentials.isValid() ? credentials : null;
        } finally {
            cursor.close();
        }
    }

    public synchronized void saveSession(LoginResult loginResult) {
        Credentials storedCredentials = getStoredCredentialsIncludingBlocked();
        saveSession(loginResult,
                storedCredentials == null ? null : storedCredentials.username,
                storedCredentials == null ? null : storedCredentials.password,
                false);
    }

    public synchronized void saveSession(LoginResult loginResult, String username, String password) {
        saveSession(loginResult, username, password, false);
    }

    private synchronized void saveSession(LoginResult loginResult, String username, String password,
                                          boolean credentialRecoveryBlocked) {
        ContentValues values = new ContentValues();
        values.put("id", 1);
        values.put("access_token", loginResult.accessToken);
        values.put("refresh_token", loginResult.refreshToken);
        values.put("token_type", loginResult.tokenType);
        long expiresAt = loginResult.accessTokenExpiresAt > 0L
                ? loginResult.accessTokenExpiresAt
                : accessTokenExpiresAt(loginResult.expiresIn);
        values.put("expires_at", expiresAt);
        values.put("user_id", loginResult.userId);
        values.put("username", username);
        values.put("password", password);
        values.put("email", loginResult.email);
        values.put("roles", join(loginResult.roles));
        values.put("credential_updated_at",
                username == null || password == null ? null : TimeUtils.now());
        values.put("credential_recovery_blocked", credentialRecoveryBlocked ? 1 : 0);
        values.put("updated_at", TimeUtils.now());
        // Current release stores credentials/tokens in plain SQLite per operating requirement.
        // Future release placeholder: encrypt username/password/refresh_token with
        // Android Keystore-backed keys before persisting, or move to an encrypted store.
        getWritableDatabase().insertWithOnConflict("auth_sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        AdcoreLogger.i(TAG, "Auth session persisted. userId=" + loginResult.userId);
    }

    public synchronized void clearSession() {
        int rows = getWritableDatabase().delete("auth_sessions", null, null);
        AdcoreLogger.i(TAG, "Auth session cleared. rows=" + rows);
    }

    public synchronized void clearTokensKeepCredentials() {
        ContentValues values = new ContentValues();
        values.putNull("access_token");
        values.putNull("refresh_token");
        values.putNull("token_type");
        values.put("expires_at", 0L);
        values.putNull("user_id");
        values.putNull("email");
        values.putNull("roles");
        values.put("updated_at", TimeUtils.now());
        int rows = getWritableDatabase().update("auth_sessions", values, "id = 1", null);
        AdcoreLogger.i(TAG, "Auth tokens cleared while credentials were retained. rows=" + rows);
    }

    public synchronized void blockCredentialRecovery() {
        ContentValues values = new ContentValues();
        values.put("credential_recovery_blocked", 1);
        values.put("updated_at", TimeUtils.now());
        int rows = getWritableDatabase().update("auth_sessions", values, "id = 1", null);
        AdcoreLogger.w(TAG, "Credential recovery blocked for stored session. rows=" + rows);
    }

    public synchronized void upsertNode(NodeItem node) {
        if (node == null || node.id == null) {
            AdcoreLogger.w(TAG, "Skipping node upsert because node/id is null.");
            return;
        }
        ContentValues values = new ContentValues();
        values.put("id", node.id);
        values.put("user_id", node.userId);
        values.put("node_name", node.nodeName);
        values.put("type_key", node.typeKey);
        values.put("device_id", node.deviceId);
        values.put("latitude", node.latitude);
        values.put("longitude", node.longitude);
        values.put("address", node.address);
        values.put("status", node.status);
        values.put("last_ping_at", node.lastPingAt);
        values.put("created_at", node.createdAt);
        values.put("updated_at", node.updatedAt);
        getWritableDatabase().insertWithOnConflict("nodes", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized void upsertResources(List<ResourceItem> resources) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            for (ResourceItem resource : resources) {
                upsertResourceLocked(db, resource);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        AdcoreLogger.i(TAG, "Resources upserted. count=" + resources.size());
    }

    public synchronized void upsertResource(ResourceItem resource) {
        upsertResourceLocked(getWritableDatabase(), resource);
    }

    public synchronized void setResourceCachePath(String resourceId, String path) {
        ContentValues values = new ContentValues();
        values.put("local_cache_path", path);
        values.put("last_downloaded_at", TimeUtils.now());
        int updated = getWritableDatabase().update("resources", values, "id = ?", new String[]{resourceId});
        AdcoreLogger.i(TAG, "Resource cache path updated. resourceId=" + resourceId + " rows=" + updated + " path=" + path);
    }

    public synchronized void deleteResource(String resourceId) {
        int deleted = getWritableDatabase().delete("resources", "id = ?", new String[]{resourceId});
        AdcoreLogger.i(TAG, "Resource deleted. resourceId=" + resourceId + " rows=" + deleted);
    }

    public synchronized void clearMappedResourcesAndNodes() {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int resources = db.delete("resources", null, null);
            int nodes = db.delete("nodes", null, null);
            db.setTransactionSuccessful();
            AdcoreLogger.w(TAG, "Mapped resource state cleared. resources=" + resources + " nodes=" + nodes);
        } finally {
            db.endTransaction();
        }
    }

    public synchronized ResourceItem getResource(String resourceId) {
        Cursor cursor = getReadableDatabase().query("resources", null, "id = ?", new String[]{resourceId}, null, null, null);
        try {
            return cursor.moveToFirst() ? ResourceItem.fromCursor(cursor) : null;
        } finally {
            cursor.close();
        }
    }

    public synchronized Map<String, ResourceItem> getAllResourcesById() {
        Map<String, ResourceItem> map = new HashMap<>();
        Cursor cursor = getReadableDatabase().query("resources", null, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                ResourceItem item = ResourceItem.fromCursor(cursor);
                if (item.id != null) {
                    map.put(item.id, item);
                }
            }
        } finally {
            cursor.close();
        }
        return map;
    }

    public synchronized List<ResourceItem> getCachedResources() {
        List<ResourceItem> result = new ArrayList<>();
        Cursor cursor = getReadableDatabase().query("resources", null,
                "local_cache_path IS NOT NULL AND local_cache_path <> ''",
                null, null, null, "display_order ASC, created_at ASC, id ASC");
        try {
            while (cursor.moveToNext()) {
                ResourceItem item = ResourceItem.fromCursor(cursor);
                if (item.localCachePath != null && new File(item.localCachePath).exists()) {
                    result.add(item);
                }
            }
        } finally {
            cursor.close();
        }
        return result;
    }

    public synchronized boolean hasCachedVideos() {
        return !getCachedResources().isEmpty();
    }

    public synchronized void incrementPlayback(String resourceId, int playedSeconds) {
        String dateKey = TimeUtils.todayKey();
        SQLiteDatabase db = getWritableDatabase();
        ContentValues insert = new ContentValues();
        insert.put("play_date", dateKey);
        insert.put("resource_id", resourceId);
        insert.put("play_count", 0);
        insert.put("total_play_seconds", 0);
        db.insertWithOnConflict("playback_counts", null, insert, SQLiteDatabase.CONFLICT_IGNORE);
        db.execSQL("UPDATE playback_counts SET play_count = play_count + 1, total_play_seconds = total_play_seconds + ? " +
                        "WHERE play_date = ? AND resource_id = ?",
                new Object[]{Math.max(0, playedSeconds), dateKey, resourceId});
    }

    public synchronized Map<String, Integer> getPlaybackCountMap(String dateKey) {
        Map<String, Integer> map = new HashMap<>();
        Cursor cursor = getReadableDatabase().query("playback_counts",
                new String[]{"resource_id", "play_count"},
                "play_date = ?", new String[]{dateKey}, null, null, null);
        try {
            while (cursor.moveToNext()) {
                map.put(cursor.getString(0), cursor.getInt(1));
            }
        } finally {
            cursor.close();
        }
        return map;
    }

    public synchronized long getTotalPlaySeconds(String dateKey) {
        return longScalar("SELECT COALESCE(SUM(total_play_seconds), 0) FROM playback_counts WHERE play_date = ?",
                new String[]{dateKey});
    }

    public synchronized RetryConfig getApiRetryConfig() {
        return new RetryConfig(
                getConfigInt(AppConstants.CONFIG_API_RETRY_MAX, 3),
                getConfigInt(AppConstants.CONFIG_API_RETRY_DELAY_SEC, 5));
    }

    public synchronized RetryConfig getBackgroundRetryConfig() {
        return new RetryConfig(
                getConfigInt(AppConstants.CONFIG_BACKGROUND_RETRY_MAX, 3),
                getConfigInt(AppConstants.CONFIG_BACKGROUND_RETRY_DELAY_SEC, 300));
    }

    public synchronized int getDownloadThreadCount() {
        return Math.max(1, getConfigInt(AppConstants.CONFIG_DOWNLOAD_THREADS, 4));
    }

    public synchronized int getConfigInt(String key, int fallback) {
        String value = getMarkerLike("config", "config_key", "config_value", key);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            AdcoreLogger.w(TAG, "Invalid integer config for " + key + ": " + value, exception);
            return fallback;
        }
    }

    public synchronized String getConfigString(String key, String fallback) {
        String value = getMarkerLike("config", "config_key", "config_value", key);
        if (value == null || value.trim().length() == 0) {
            return fallback;
        }
        return value.trim();
    }

    public synchronized String getMarker(String key) {
        return getMarkerLike("markers", "marker_key", "marker_value", key);
    }

    public synchronized void setMarker(String key, String value) {
        ContentValues values = new ContentValues();
        values.put("marker_key", key);
        values.put("marker_value", value);
        getWritableDatabase().insertWithOnConflict("markers", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public synchronized long startSyncRun(String type) {
        ContentValues values = new ContentValues();
        values.put("sync_type", type);
        values.put("started_at", TimeUtils.now());
        values.put("success", 0);
        long id = getWritableDatabase().insert("sync_runs", null, values);
        AdcoreLogger.i(TAG, "Sync run started. id=" + id + " type=" + type);
        return id;
    }

    public synchronized void completeSyncRun(long runId, boolean success, String details) {
        Cursor cursor = getReadableDatabase().query("sync_runs", new String[]{"started_at"},
                "id = ?", new String[]{String.valueOf(runId)}, null, null, null);
        long startedAt = TimeUtils.now();
        try {
            if (cursor.moveToFirst()) {
                startedAt = cursor.getLong(0);
            }
        } finally {
            cursor.close();
        }
        long completedAt = TimeUtils.now();
        ContentValues values = new ContentValues();
        values.put("completed_at", completedAt);
        values.put("success", success ? 1 : 0);
        values.put("total_time_sec", Math.max(0L, (completedAt - startedAt) / 1000L));
        values.put("details", details);
        getWritableDatabase().update("sync_runs", values, "id = ?", new String[]{String.valueOf(runId)});
    }

    public synchronized void recordApiMetric(long syncRunId, String apiName, String resourceId,
                                             long startedAt, long completedAt, int retries,
                                             boolean success, String message) {
        ContentValues values = new ContentValues();
        values.put("sync_run_id", syncRunId);
        values.put("api_name", apiName);
        values.put("resource_id", resourceId);
        values.put("started_at", startedAt);
        values.put("completed_at", completedAt);
        values.put("duration_ms", Math.max(0L, completedAt - startedAt));
        values.put("retries", retries);
        values.put("success", success ? 1 : 0);
        values.put("message", message);
        getWritableDatabase().insert("api_metrics", null, values);
    }

    public synchronized long getTotalSuccessfulSyncSeconds(String dateKey) {
        long start = TimeUtils.startOfLocalDay(dateKey);
        long end = TimeUtils.endOfLocalDayExclusive(dateKey);
        return longScalar("SELECT COALESCE(SUM(total_time_sec), 0) FROM sync_runs " +
                "WHERE success = 1 AND completed_at >= ? AND completed_at < ?", new String[]{String.valueOf(start), String.valueOf(end)});
    }

    public synchronized void startUptimeSession(String type, long startedAt) {
        closeOpenUptimeSessions(type, startedAt);
        ContentValues values = new ContentValues();
        values.put("session_type", type);
        values.put("started_at", startedAt);
        getWritableDatabase().insert("uptime_sessions", null, values);
        AdcoreLogger.i(TAG, "Uptime session started. type=" + type + " startedAt=" + startedAt);
    }

    public synchronized void closeOpenUptimeSessions(String type, long endedAt) {
        ContentValues values = new ContentValues();
        values.put("ended_at", endedAt);
        getWritableDatabase().update("uptime_sessions", values,
                "session_type = ? AND ended_at IS NULL", new String[]{type});
    }

    public synchronized boolean hasOpenUptimeSession(String type) {
        Cursor cursor = getReadableDatabase().query("uptime_sessions", new String[]{"id"},
                "session_type = ? AND ended_at IS NULL", new String[]{type}, null, null, null);
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public synchronized long getTotalUptimeSeconds(String type, String dateKey) {
        long dayStart = TimeUtils.startOfLocalDay(dateKey);
        long dayEnd = TimeUtils.endOfLocalDayExclusive(dateKey);
        long now = TimeUtils.now();
        long totalMs = 0L;
        Cursor cursor = getReadableDatabase().query("uptime_sessions",
                new String[]{"started_at", "ended_at"},
                "session_type = ? AND started_at < ? AND (ended_at IS NULL OR ended_at > ?)",
                new String[]{type, String.valueOf(dayEnd), String.valueOf(dayStart)},
                null, null, "started_at ASC");
        try {
            while (cursor.moveToNext()) {
                long start = Math.max(cursor.getLong(0), dayStart);
                long end = cursor.isNull(1) ? Math.min(now, dayEnd) : Math.min(cursor.getLong(1), dayEnd);
                if (end > start) {
                    totalMs += end - start;
                }
            }
        } finally {
            cursor.close();
        }
        return totalMs / 1000L;
    }

    public synchronized boolean wasErrorSignatureSent(String signature) {
        Cursor cursor = getReadableDatabase().query("sent_error_logs", new String[]{"signature"},
                "signature = ?", new String[]{signature}, null, null, null);
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    public synchronized void markErrorSignatureSent(String signature) {
        ContentValues values = new ContentValues();
        values.put("signature", signature);
        values.put("sent_at", TimeUtils.now());
        getWritableDatabase().insertWithOnConflict("sent_error_logs", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public File databaseFile() {
        return appContext.getDatabasePath(AppConstants.DB_NAME);
    }

    private void upsertResourceLocked(SQLiteDatabase db, ResourceItem resource) {
        if (resource == null || resource.id == null) {
            AdcoreLogger.w(TAG, "Skipping resource upsert because resource/id is null.");
            return;
        }
        ResourceItem existing = getResource(resource.id);
        ContentValues values = new ContentValues();
        values.put("id", resource.id);
        values.put("user_id", resource.userId);
        values.put("file_name", resource.fileName);
        values.put("file_uri", resource.fileUri);
        values.put("media_type", resource.mediaType);
        values.put("file_size_bytes", resource.fileSizeBytes);
        values.put("checksum", resource.checksum);
        values.put("duration_seconds", resource.durationSeconds);
        values.put("type_key", resource.typeKey);
        values.put("status", resource.status);
        values.put("display_order", resource.displayOrder);
        values.put("start_date", resource.startDate);
        values.put("end_date", resource.endDate);
        values.put("display_count", resource.displayCount);
        values.put("created_at", resource.createdAt);
        values.put("updated_at", resource.updatedAt);
        values.put("local_cache_path", existing == null ? resource.localCachePath : existing.localCachePath);
        values.put("last_downloaded_at", existing == null ? resource.lastDownloadedAt : existing.lastDownloadedAt);
        db.insertWithOnConflict("resources", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private long longScalar(String sql, String[] args) {
        Cursor cursor = getReadableDatabase().rawQuery(sql, args);
        try {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0L;
        } finally {
            cursor.close();
        }
    }

    private String getMarkerLike(String table, String keyColumn, String valueColumn, String key) {
        Cursor cursor = getReadableDatabase().query(table, new String[]{valueColumn},
                keyColumn + " = ?", new String[]{key}, null, null, null);
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    private static String cursorValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getString(index) : null;
    }

    private static String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(values.get(i));
        }
        return builder.toString();
    }

    private void insertDefaultConfig(SQLiteDatabase db) {
        putDefault(db, AppConstants.CONFIG_API_RETRY_DELAY_SEC, "5");
        putDefault(db, AppConstants.CONFIG_API_RETRY_MAX, "3");
        putDefault(db, AppConstants.CONFIG_BACKGROUND_RETRY_DELAY_SEC, "300");
        putDefault(db, AppConstants.CONFIG_BACKGROUND_RETRY_MAX, "3");
        putDefault(db, AppConstants.CONFIG_DOWNLOAD_THREADS, "4");
        putDefault(db, AppConstants.CONFIG_DAILY_SYNC_TIME, AppConstants.DEFAULT_DAILY_SYNC_TIME);
    }

    private void createAuthSessionTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS auth_sessions (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1)," +
                "username TEXT," +
                "password TEXT," +
                "access_token TEXT," +
                "refresh_token TEXT," +
                "token_type TEXT," +
                "expires_at INTEGER," +
                "user_id TEXT," +
                "email TEXT," +
                "roles TEXT," +
                "credential_updated_at INTEGER," +
                "credential_recovery_blocked INTEGER NOT NULL DEFAULT 0," +
                "updated_at INTEGER NOT NULL)");
    }

    private void ensureAuthSessionColumns(SQLiteDatabase db) {
        addColumnIfMissing(db, "auth_sessions", "username", "TEXT");
        addColumnIfMissing(db, "auth_sessions", "password", "TEXT");
        addColumnIfMissing(db, "auth_sessions", "credential_updated_at", "INTEGER");
        addColumnIfMissing(db, "auth_sessions", "credential_recovery_blocked", "INTEGER NOT NULL DEFAULT 0");
    }

    private void migrateAndRemoveLegacyCredentialsTable(SQLiteDatabase db) {
        if (!tableExists(db, "credentials")) {
            return;
        }
        Cursor cursor = db.query("credentials",
                new String[]{"access_token", "refresh_token", "token_type", "expires_at", "user_id", "email", "roles", "updated_at"},
                "id = 1", null, null, null, null);
        try {
            if (cursor.moveToFirst()) {
                String refreshToken = cursorValue(cursor, "refresh_token");
                if (refreshToken != null && refreshToken.trim().length() > 0) {
                    ContentValues values = new ContentValues();
                    values.put("id", 1);
                    values.put("username", cursorValue(cursor, "username"));
                    values.put("password", cursorValue(cursor, "password"));
                    values.put("access_token", cursorValue(cursor, "access_token"));
                    values.put("refresh_token", refreshToken);
                    values.put("token_type", cursorValue(cursor, "token_type"));
                    values.put("expires_at", cursorValue(cursor, "expires_at"));
                    values.put("user_id", cursorValue(cursor, "user_id"));
                    values.put("email", cursorValue(cursor, "email"));
                    values.put("roles", cursorValue(cursor, "roles"));
                    values.put("credential_updated_at", TimeUtils.now());
                    values.put("credential_recovery_blocked", 0);
                    values.put("updated_at", TimeUtils.now());
                    db.insertWithOnConflict("auth_sessions", null, values, SQLiteDatabase.CONFLICT_REPLACE);
                    AdcoreLogger.i(TAG, "Legacy credential table migrated to auth session.");
                }
            }
        } finally {
            cursor.close();
        }
        db.execSQL("DROP TABLE IF EXISTS credentials");
        AdcoreLogger.i(TAG, "Legacy credential table removed.");
    }

    private boolean tableExists(SQLiteDatabase db, String tableName) {
        Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name = ?",
                new String[]{tableName});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    private void addColumnIfMissing(SQLiteDatabase db, String tableName, String columnName, String columnDefinition) {
        if (!columnExists(db, tableName, columnName)) {
            db.execSQL("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + columnDefinition);
        }
    }

    private boolean columnExists(SQLiteDatabase db, String tableName, String columnName) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + tableName + ")", null);
        try {
            while (cursor.moveToNext()) {
                if (columnName.equals(cursor.getString(1))) {
                    return true;
                }
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    private Credentials getStoredCredentialsIncludingBlocked() {
        Cursor cursor = getReadableDatabase().query("auth_sessions",
                new String[]{"username", "password"}, "id = 1", null, null, null, null);
        try {
            if (!cursor.moveToFirst()) {
                return null;
            }
            Credentials credentials = new Credentials(cursor.getString(0), cursor.getString(1));
            return credentials.isValid() ? credentials : null;
        } finally {
            cursor.close();
        }
    }

    private long accessTokenExpiresAt(long expiresInSeconds) {
        long ttlMs = expiresInSeconds > 0L ? expiresInSeconds * 1000L : AppConstants.DEFAULT_ACCESS_TOKEN_TTL_MS;
        return TimeUtils.now() + ttlMs;
    }

    private void putDefault(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("config_key", key);
        values.put("config_value", value);
        db.insertWithOnConflict("config", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }
}
