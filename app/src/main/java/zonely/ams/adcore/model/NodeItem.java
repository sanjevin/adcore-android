package zonely.ams.adcore.model;

import org.json.JSONObject;

public class NodeItem {
    public String id;
    public String userId;
    public String nodeName;
    public String typeKey;
    public String deviceId;
    public double latitude;
    public double longitude;
    public String address;
    public String status;
    public String lastPingAt;
    public String createdAt;
    public String updatedAt;

    public static NodeItem fromJson(JSONObject json) {
        NodeItem item = new NodeItem();
        if (json == null) {
            return item;
        }
        item.id = json.optString("id", null);
        item.userId = json.optString("userId", null);
        item.nodeName = json.optString("nodeName", null);
        item.typeKey = json.optString("typeKey", null);
        item.deviceId = json.optString("deviceId", null);
        item.latitude = json.optDouble("latitude", 0D);
        item.longitude = json.optDouble("longitude", 0D);
        item.address = json.optString("address", null);
        item.status = json.optString("status", null);
        item.lastPingAt = json.optString("lastPingAt", null);
        item.createdAt = json.optString("createdAt", null);
        item.updatedAt = json.optString("updatedAt", null);
        return item;
    }
}
