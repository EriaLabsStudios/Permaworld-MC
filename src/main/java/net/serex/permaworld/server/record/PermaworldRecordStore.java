package net.serex.permaworld.server.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.serex.permaworld.Permaworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class PermaworldRecordStore {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final String RECORD_FILE = "records.jsonl";
    private final Path root;

    public PermaworldRecordStore(Path root) {
        this.root = root;
    }

    public static PermaworldRecordStore forServer(MinecraftServer server) {
        return new PermaworldRecordStore(server.getWorldPath(LevelResource.ROOT).resolve("permaworld"));
    }

    public void appendPlayerRecord(UUID playerUuid, JsonObject record) throws IOException {
        append(playerRecordPath(playerUuid), record);
    }

    public void appendServerRecord(JsonObject record) throws IOException {
        append(root.resolve("server-records.jsonl"), record);
    }

    public List<RecordSummary> latestPlayerRecords(UUID playerUuid, int limit) throws IOException {
        return latestPlayerRecords(playerUuid, limit, null);
    }

    public List<RecordSummary> latestPlayerRecords(UUID playerUuid, int limit, String reasonFilter) throws IOException {
        List<JsonObject> records = readPlayerRecords(playerUuid);
        Collections.reverse(records);
        return records.stream()
                .filter(record -> matchesReason(record, reasonFilter))
                .limit(Math.max(0, limit))
                .map(RecordSummary::fromJson)
                .toList();
    }

    public Optional<JsonObject> findPlayerRecord(UUID playerUuid, String recordId) throws IOException {
        return readPlayerRecords(playerUuid).stream()
                .filter(record -> record.has("id") && recordId.equals(record.get("id").getAsString()))
                .findFirst();
    }

    public List<JsonObject> readPlayerRecords(UUID playerUuid) throws IOException {
        Path path = playerRecordPath(playerUuid);
        if (!Files.exists(path)) {
            return List.of();
        }
        List<JsonObject> records = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) {
                continue;
            }
            try {
                records.add(JsonParser.parseString(line).getAsJsonObject());
            } catch (RuntimeException e) {
                Permaworld.LOGGER.warn("Registro Permaworld ignorado por JSON invalido en {}", path, e);
            }
        }
        return records;
    }

    public List<UUID> knownPlayerIds() throws IOException {
        Path playersRoot = root.resolve("players");
        if (!Files.isDirectory(playersRoot)) {
            return List.of();
        }
        List<UUID> playerIds = new ArrayList<>();
        try (var paths = Files.list(playersRoot)) {
            paths.filter(Files::isDirectory).forEach(path -> {
                try {
                    playerIds.add(UUID.fromString(path.getFileName().toString()));
                } catch (IllegalArgumentException ignored) {
                    Permaworld.LOGGER.warn("Directorio de jugador invalido ignorado en {}", path);
                }
            });
        }
        return playerIds;
    }

    private Path playerRecordPath(UUID playerUuid) {
        return root.resolve("players").resolve(playerUuid.toString()).resolve(RECORD_FILE);
    }

    private static boolean matchesReason(JsonObject record, String reasonFilter) {
        if (reasonFilter == null || reasonFilter.isBlank()) {
            return true;
        }
        if (!record.has("reason") || record.get("reason").isJsonNull()) {
            return false;
        }
        return normalizeReasonFilter(reasonFilter).equals(normalizeReasonFilter(record.get("reason").getAsString()));
    }

    public static String normalizeReasonFilter(String reason) {
        String normalized = reason == null ? "" : reason.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "DEATHS", "MUERTE", "MUERTES" -> "DEATH";
            case "PATH", "PATHS", "CAMINO", "RECORRIDO" -> "PATH_SAMPLE";
            case "GAMEMODE", "GAME_MODE", "GAME_MODE_CHANGES" -> "GAME_MODE_CHANGE";
            case "DIMENSION", "DIMENSION_CHANGE", "DIMENSIONES" -> "DIMENSION_CHANGE";
            case "ADVANCEMENT", "ADVANCEMENTS", "LOGRO", "LOGROS" -> "ADVANCEMENT_DONE";
            case "RESPAWNS" -> "RESPAWN";
            case "SNAPSHOT", "SNAPSHOTS", "MANUAL" -> "MANUAL_SNAPSHOT";
            default -> normalized;
        };
    }

    private static void append(Path path, JsonObject record) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(record) + System.lineSeparator(),
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
