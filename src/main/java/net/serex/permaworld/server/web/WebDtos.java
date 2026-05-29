package net.serex.permaworld.server.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.serex.permaworld.server.record.PermaworldRecordStore;

import java.util.List;

public final class WebDtos {

    private WebDtos() {
    }

    public static JsonObject session(List<String> adminNames) {
        JsonObject json = new JsonObject();
        JsonArray admins = new JsonArray();
        for (String adminName : adminNames) {
            admins.add(adminName);
        }
        json.add("admins", admins);
        json.addProperty("defaultFilter", "DEATH");
        return json;
    }

    public static JsonObject playerSummary(String uuid, String playerName, String lastReason, String lastTimestamp, int recordCount, long logSizeBytes) {
        JsonObject json = new JsonObject();
        json.addProperty("uuid", uuid);
        json.addProperty("playerName", playerName);
        json.addProperty("lastReason", label(lastReason));
        json.addProperty("lastReasonKey", normalize(lastReason));
        json.addProperty("lastTimestamp", lastTimestamp);
        json.addProperty("recordCount", recordCount);
        json.addProperty("logSizeBytes", logSizeBytes);
        json.addProperty("logSize", formatSize(logSizeBytes));
        return json;
    }

    private static String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format(java.util.Locale.US, "%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }

    public static JsonObject stat(String key, String label, int value, String formatted) {
        JsonObject json = new JsonObject();
        json.addProperty("key", key);
        json.addProperty("label", label);
        json.addProperty("value", value);
        json.addProperty("formatted", formatted);
        return json;
    }

    public static JsonObject leaderboardEntry(String key, String label, int value) {
        JsonObject json = new JsonObject();
        json.addProperty("key", key);
        json.addProperty("label", label);
        json.addProperty("value", value);
        return json;
    }

    public static JsonObject recordCard(JsonObject record) {
        JsonObject json = new JsonObject();
        json.addProperty("id", string(record, "id"));
        json.addProperty("type", string(record, "type"));
        json.addProperty("reason", label(string(record, "reason")));
        json.addProperty("reasonKey", normalize(string(record, "reason")));
        json.addProperty("timestamp", string(record, "timestamp"));
        json.addProperty("playerName", string(record, "playerName"));
        json.addProperty("dimension", string(record, "dimension"));
        json.addProperty("itemCount", intValue(record, "itemCount"));
        json.addProperty("restorable", "inventory_snapshot".equals(string(record, "type")));
        json.addProperty("summary", summary(record));
        return json;
    }

    public static JsonObject recordDetail(JsonObject record) {
        JsonObject json = recordCard(record);
        json.add("items", itemDtos(record));
        if (record.has("position")) {
            json.add("position", record.get("position").deepCopy());
        }
        if (record.has("metadata")) {
            json.add("metadata", record.get("metadata").deepCopy());
        }
        return json;
    }

    public static JsonObject restoreResult(boolean ok, String message, int restoredStacks) {
        JsonObject json = new JsonObject();
        json.addProperty("ok", ok);
        json.addProperty("message", message);
        json.addProperty("restoredStacks", restoredStacks);
        return json;
    }

    private static JsonArray itemDtos(JsonObject record) {
        JsonArray items = new JsonArray();
        if (!record.has("items") || !record.get("items").isJsonArray()) {
            return items;
        }
        for (var element : record.getAsJsonArray("items")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject source = element.getAsJsonObject();
            JsonObject item = new JsonObject();
            item.addProperty("section", string(source, "section"));
            item.addProperty("slot", intValue(source, "slot"));
            item.addProperty("itemId", string(source, "itemId"));
            item.addProperty("count", intValue(source, "count"));
            item.addProperty("customName", string(source, "customName"));
            items.add(item);
        }
        return items;
    }

    private static String summary(JsonObject record) {
        String reason = normalize(string(record, "reason"));
        int itemCount = intValue(record, "itemCount");
        if ("STRUCTURE_DISCOVERED".equals(reason)) {
            if (record.has("metadata") && record.get("metadata").isJsonObject()) {
                JsonObject meta = record.getAsJsonObject("metadata");
                if (meta.has("name")) {
                    return "Discovered " + meta.get("name").getAsString();
                }
            }
            return "Discovered structure";
        }
        return switch (reason) {
            case "DEATH" -> itemCount + " stacks recoverable";
            case "RESPAWN" -> "Player respawned";
            case "PATH_SAMPLE" -> "Movement sample";
            case "GAME_MODE_CHANGE" -> "Game mode changed";
            case "DIMENSION_CHANGE" -> "Dimension changed";
            case "CURRENT_STATE" -> "Current live/logout state";
            case "ADVANCEMENT_DONE" -> string(record, "advancementTitle").isBlank()
                    ? "Advancement completed"
                    : string(record, "advancementTitle");
            default -> itemCount + " stacks";
        };
    }

    public static String label(String reason) {
        return switch (normalize(reason)) {
            case "DEATH" -> "Death";
            case "RESPAWN" -> "Respawn";
            case "PATH_SAMPLE" -> "Path Sample";
            case "GAME_MODE_CHANGE" -> "Game Mode Change";
            case "DIMENSION_CHANGE" -> "Dimension Change";
            case "ADVANCEMENT_DONE" -> "Advancement";
            case "JOIN" -> "Join";
            case "DISCONNECT" -> "Disconnect";
            case "MANUAL_SNAPSHOT" -> "Manual Snapshot";
            case "CURRENT_STATE" -> "Current State";
            case "STRUCTURE_DISCOVERED" -> "Structure Discovered";
            default -> reason == null || reason.isBlank() ? "Unknown" : reason;
        };
    }

    public static String normalize(String reason) {
        return PermaworldRecordStore.normalizeReasonFilter(reason);
    }

    private static String string(JsonObject record, String key) {
        if (!record.has(key) || record.get(key).isJsonNull()) {
            return "";
        }
        return record.get(key).getAsString();
    }

    private static int intValue(JsonObject record, String key) {
        if (!record.has(key) || !record.get(key).isJsonPrimitive()) {
            return 0;
        }
        return record.get(key).getAsInt();
    }
}
