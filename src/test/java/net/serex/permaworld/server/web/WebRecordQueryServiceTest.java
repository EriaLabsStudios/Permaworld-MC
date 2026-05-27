package net.serex.permaworld.server.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.serex.permaworld.server.record.PermaworldRecordStore;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRecordQueryServiceTest {

    @Test
    void mapsPlayerSummariesAndFiltersNewestFirst() throws IOException {
        Path root = Files.createTempDirectory("permaworld-web-records");
        PermaworldRecordStore store = new PermaworldRecordStore(root);
        UUID playerId = UUID.randomUUID();
        store.appendPlayerRecord(playerId, record("one", "PATH_SAMPLE", "Serex", "2026-05-26T10:00:00Z", 0));
        store.appendPlayerRecord(playerId, record("two", "DEATH", "Serex", "2026-05-26T10:05:00Z", 4));

        WebRecordQueryService service = new WebRecordQueryService(null, store);
        JsonArray records = service.playerRecords(playerId, "DEATH");
        JsonObject summary = service.playerSummaries().get(0).getAsJsonObject();

        assertEquals(1, records.size());
        assertEquals("Death", records.get(0).getAsJsonObject().get("reason").getAsString());
        assertEquals("Serex", summary.get("playerName").getAsString());
        assertEquals("Death", summary.get("lastReason").getAsString());
    }

    @Test
    void detailIncludesHumanLabelsAndItemRows() {
        JsonObject detail = WebDtos.recordDetail(record("three", "DEATH", "Serex", "2026-05-26T10:05:00Z", 2));

        assertEquals("Death", detail.get("reason").getAsString());
        assertTrue(detail.get("restorable").getAsBoolean());
        assertEquals(1, detail.getAsJsonArray("items").size());
    }

    @Test
    void advancementSummaryUsesResolvedTitleWhenPresent() {
        JsonObject record = record("adv", "ADVANCEMENT_DONE", "Serex", "2026-05-26T10:05:00Z", 0);
        record.addProperty("advancementTitle", "Monster Hunter");

        JsonObject detail = WebDtos.recordCard(record);

        assertEquals("Monster Hunter", detail.get("summary").getAsString());
    }

    @Test
    void testOfflineStatsUnavailableGracefully() throws IOException {
        Path root = Files.createTempDirectory("permaworld-web-records-stats");
        PermaworldRecordStore store = new PermaworldRecordStore(root);
        UUID playerId = UUID.randomUUID();
        WebRecordQueryService service = new WebRecordQueryService(null, store);
        JsonObject stats = service.playerStats(playerId);
        
        // When server is null, it should gracefully fall back to unavailable
        assertTrue(stats.has("available"));
        assertEquals(false, stats.get("available").getAsBoolean());
    }

    private static JsonObject record(String id, String reason, String playerName, String timestamp, int itemCount) {
        JsonObject record = new JsonObject();
        record.addProperty("schemaVersion", 1);
        record.addProperty("type", "inventory_snapshot");
        record.addProperty("id", id);
        record.addProperty("timestamp", timestamp);
        record.addProperty("reason", reason);
        record.addProperty("playerName", playerName);
        record.addProperty("dimension", "minecraft:overworld");
        record.addProperty("itemCount", itemCount);
        JsonArray items = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("section", "inventory");
        item.addProperty("slot", 0);
        item.addProperty("itemId", "minecraft:diamond");
        item.addProperty("count", 12);
        items.add(item);
        record.add("items", items);
        return record;
    }
}
