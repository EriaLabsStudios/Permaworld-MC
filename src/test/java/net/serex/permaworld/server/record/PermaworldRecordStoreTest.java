package net.serex.permaworld.server.record;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermaworldRecordStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void appendsPlayerRecordsAsJsonLinesAndReadsNewestFirst() throws IOException {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000015");
        PermaworldRecordStore store = new PermaworldRecordStore(tempDir);

        store.appendPlayerRecord(playerId, record("old", "JOIN", "Adan", "2026-05-26T10:00:00Z"));
        store.appendPlayerRecord(playerId, record("new", "DEATH", "Adan", "2026-05-26T10:05:00Z"));

        List<RecordSummary> latest = store.latestPlayerRecords(playerId, 10);

        assertEquals(2, latest.size());
        assertEquals("new", latest.get(0).id());
        assertEquals("DEATH", latest.get(0).reason());
        assertEquals("old", latest.get(1).id());
        assertTrue(Files.readString(tempDir.resolve("players").resolve(playerId.toString()).resolve("records.jsonl"))
                .contains("\"reason\":\"JOIN\""));
    }

    @Test
    void limitsResultsAndIgnoresMalformedLines() throws IOException {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000018");
        PermaworldRecordStore store = new PermaworldRecordStore(tempDir);
        Path file = tempDir.resolve("players").resolve(playerId.toString()).resolve("records.jsonl");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "not-json\n");

        store.appendPlayerRecord(playerId, record("one", "JOIN", "Adan", "2026-05-26T10:00:00Z"));
        store.appendPlayerRecord(playerId, record("two", "DISCONNECT", "Adan", "2026-05-26T10:01:00Z"));

        List<RecordSummary> latest = store.latestPlayerRecords(playerId, 1);

        assertEquals(1, latest.size());
        assertEquals("two", latest.get(0).id());
    }

    @Test
    void findsRecordById() throws IOException {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000019");
        PermaworldRecordStore store = new PermaworldRecordStore(tempDir);
        store.appendPlayerRecord(playerId, record("wanted", "MANUAL_SNAPSHOT", "Adan", "2026-05-26T10:00:00Z"));

        JsonObject found = store.findPlayerRecord(playerId, "wanted").orElseThrow();

        assertEquals("inventory_snapshot", found.get("type").getAsString());
        assertEquals("MANUAL_SNAPSHOT", found.get("reason").getAsString());
    }

    @Test
    void canFilterLatestRecordsByReason() throws IOException {
        UUID playerId = UUID.fromString("00000000-0000-0000-0000-000000000020");
        PermaworldRecordStore store = new PermaworldRecordStore(tempDir);
        store.appendPlayerRecord(playerId, record("path", "PATH_SAMPLE", "Adan", "2026-05-26T10:00:00Z"));
        store.appendPlayerRecord(playerId, record("death", "DEATH", "Adan", "2026-05-26T10:01:00Z"));
        store.appendPlayerRecord(playerId, record("mode", "GAME_MODE_CHANGE", "Adan", "2026-05-26T10:02:00Z"));

        List<RecordSummary> deaths = store.latestPlayerRecords(playerId, 10, "death");

        assertEquals(1, deaths.size());
        assertEquals("death", deaths.getFirst().id());
        assertEquals("DEATH", deaths.getFirst().reason());
    }

    private static JsonObject record(String id, String reason, String playerName, String timestamp) {
        JsonObject record = new JsonObject();
        record.addProperty("schemaVersion", 1);
        record.addProperty("type", "inventory_snapshot");
        record.addProperty("id", id);
        record.addProperty("timestamp", timestamp);
        record.addProperty("reason", reason);
        record.addProperty("playerName", playerName);
        record.addProperty("itemCount", 3);
        return record;
    }
}
