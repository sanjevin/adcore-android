package zonely.ams.adcore.model;

import android.database.Cursor;

import org.json.JSONObject;

public class ResourceItem {
    public String id;
    public String userId;
    public String fileName;
    public String fileUri;
    public String mediaType;
    public long fileSizeBytes;
    public String checksum;
    public int durationSeconds;
    public String typeKey;
    public String status;
    public int displayOrder;
    public String startDate;
    public String endDate;
    public int displayCount;
    public String createdAt;
    public String updatedAt;
    public String localCachePath;
    public long lastDownloadedAt;

    public static ResourceItem fromJson(JSONObject json) {
        ResourceItem item = new ResourceItem();
        if (json == null) {
            return item;
        }
        item.id = json.optString("id", null);
        item.userId = json.optString("userId", null);
        item.fileName = json.optString("fileName", null);
        item.fileUri = json.optString("fileUri", null);
        item.mediaType = json.optString("mediaType", null);
        item.fileSizeBytes = json.optLong("fileSizeBytes", 0L);
        item.checksum = json.optString("checksum", null);
        item.durationSeconds = json.optInt("durationSeconds", 0);
        item.typeKey = json.optString("typeKey", null);
        item.status = json.optString("status", null);
        item.displayOrder = json.optInt("displayOrder", 0);
        item.startDate = json.optString("startDate", null);
        item.endDate = json.optString("endDate", null);
        item.displayCount = json.optInt("displayCount", 0);
        item.createdAt = json.optString("createdAt", null);
        item.updatedAt = json.optString("updatedAt", null);
        return item;
    }

    public static ResourceItem fromCursor(Cursor cursor) {
        ResourceItem item = new ResourceItem();
        item.id = getString(cursor, "id");
        item.userId = getString(cursor, "user_id");
        item.fileName = getString(cursor, "file_name");
        item.fileUri = getString(cursor, "file_uri");
        item.mediaType = getString(cursor, "media_type");
        item.fileSizeBytes = getLong(cursor, "file_size_bytes");
        item.checksum = getString(cursor, "checksum");
        item.durationSeconds = getInt(cursor, "duration_seconds");
        item.typeKey = getString(cursor, "type_key");
        item.status = getString(cursor, "status");
        item.displayOrder = getInt(cursor, "display_order");
        item.startDate = getString(cursor, "start_date");
        item.endDate = getString(cursor, "end_date");
        item.displayCount = getInt(cursor, "display_count");
        item.createdAt = getString(cursor, "created_at");
        item.updatedAt = getString(cursor, "updated_at");
        item.localCachePath = getString(cursor, "local_cache_path");
        item.lastDownloadedAt = getLong(cursor, "last_downloaded_at");
        return item;
    }

    public boolean hasSameServerProperties(ResourceItem other) {
        if (other == null) {
            return false;
        }
        return eq(userId, other.userId)
                && eq(fileName, other.fileName)
                && eq(fileUri, other.fileUri)
                && eq(mediaType, other.mediaType)
                && fileSizeBytes == other.fileSizeBytes
                && eq(checksum, other.checksum)
                && durationSeconds == other.durationSeconds
                && eq(typeKey, other.typeKey)
                && eq(status, other.status)
                && displayOrder == other.displayOrder
                && eq(startDate, other.startDate)
                && eq(endDate, other.endDate)
                && displayCount == other.displayCount
                && eq(createdAt, other.createdAt)
                && eq(updatedAt, other.updatedAt);
    }

    private static boolean eq(String left, String right) {
        if (left == null) {
            return right == null;
        }
        return left.equals(right);
    }

    private static String getString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getString(index) : null;
    }

    private static int getInt(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getInt(index) : 0;
    }

    private static long getLong(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        return index >= 0 && !cursor.isNull(index) ? cursor.getLong(index) : 0L;
    }
}
