package net.serex.permaworld.server.record;

import com.google.gson.JsonObject;

public record RecordSummary(String id, String type, String reason, String timestamp, String playerName, int itemCount) {

    public static RecordSummary fromJson(JsonObject json) {
        return new RecordSummary(
                string(json, "id"),
                string(json, "type"),
                string(json, "reason"),
                string(json, "timestamp"),
                string(json, "playerName"),
                json.has("itemCount") && json.get("itemCount").isJsonPrimitive() ? json.get("itemCount").getAsInt() : 0
        );
    }

    private static String string(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return "";
        }
        return json.get(key).getAsString();
    }
}
