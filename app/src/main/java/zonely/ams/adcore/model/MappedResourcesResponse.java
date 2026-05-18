package zonely.ams.adcore.model;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class MappedResourcesResponse {
    public NodeItem node;
    public final List<ResourceItem> resources = new ArrayList<>();
    public String message;

    public static MappedResourcesResponse fromJson(JSONObject root) {
        MappedResourcesResponse response = new MappedResourcesResponse();
        response.message = root == null ? null : root.optString("message", null);
        JSONObject data = root == null ? null : root.optJSONObject("data");
        if (data == null) {
            return response;
        }
        response.node = NodeItem.fromJson(data.optJSONObject("nodeResponse"));
        JSONArray array = data.optJSONArray("responses");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                response.resources.add(ResourceItem.fromJson(array.optJSONObject(i)));
            }
        }
        return response;
    }
}
