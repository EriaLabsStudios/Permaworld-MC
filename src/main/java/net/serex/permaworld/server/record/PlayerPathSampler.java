package net.serex.permaworld.server.record;

import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
import net.serex.permaworld.Permaworld;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerPathSampler {

    private static final int SAMPLE_INTERVAL_TICKS = 200;
    private static final double MIN_DISTANCE_SQUARED = 8.0 * 8.0;

    private final Map<UUID, LastPlayerState> states = new HashMap<>();
    private final Map<UUID, Vec3> lastPathSamples = new HashMap<>();
    private int ticks;

    public void tick(MinecraftServer server) {
        ticks++;
        boolean samplePath = ticks % SAMPLE_INTERVAL_TICKS == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            sample(player, server, samplePath);
        }
    }

    private void sample(ServerPlayer player, MinecraftServer server, boolean samplePath) {
        UUID uuid = player.getUUID();
        String dimension = player.level().dimension().identifier().toString();
        String gameMode = player.gameMode.getGameModeForPlayer().getName();
        Vec3 position = player.position();
        boolean alive = player.isAlive();
        LastPlayerState previous = states.get(uuid);

        if (previous != null) {
            if (!previous.dimension.equals(dimension)) {
                JsonObject metadata = new JsonObject();
                metadata.addProperty("from", previous.dimension);
                metadata.addProperty("to", dimension);
                InventorySnapshotService.appendActivity(server, player, "DIMENSION_CHANGE", metadata);
            }
            if (!previous.gameMode.equals(gameMode)) {
                JsonObject metadata = new JsonObject();
                metadata.addProperty("from", previous.gameMode);
                metadata.addProperty("to", gameMode);
                InventorySnapshotService.appendActivity(server, player, "GAME_MODE_CHANGE", metadata);
            }
            if (!previous.alive && alive) {
                InventorySnapshotService.appendActivity(server, player, "RESPAWN", new JsonObject());
            }
            if (samplePath && shouldRecordPath(uuid, position)) {
                InventorySnapshotService.appendActivity(server, player, "PATH_SAMPLE", new JsonObject());
                lastPathSamples.put(uuid, position);
            }
        } else if (samplePath) {
            InventorySnapshotService.appendActivity(server, player, "PATH_SAMPLE", new JsonObject());
            lastPathSamples.put(uuid, position);
        }

        if (samplePath) {
            try {
                net.minecraft.core.BlockPos pos = player.blockPosition();
                if (!(player.level() instanceof ServerLevel world)) return;

                // getAllStructuresAt es más eficiente: solo devuelve las estructuras presentes en ese bloque
                Map<Structure, it.unimi.dsi.fastutil.longs.LongSet> allStructures =
                        world.structureManager().getAllStructuresAt(pos);

                for (Map.Entry<Structure, it.unimi.dsi.fastutil.longs.LongSet> entry : allStructures.entrySet()) {
                    Structure structure = entry.getKey();
                    StructureStart start = world.structureManager().getStructureAt(pos, structure);
                    if (start == null || !start.isValid()) continue;

                    Identifier structureId = world.registryAccess()
                            .lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE)
                            .getKey(structure);
                    if (structureId == null) continue;

                    String structKey = structureId.toString();
                    String coordKey = start.getChunkPos().toString();

                    // Chequeo de duplicados via caché en memoria (no re-lee el JSONL cada tick)
                    ExtendedStatsManager.PlayerStats stats = ExtendedStatsManager.get(server, player.getUUID());
                    String dedupKey = structKey + "@" + coordKey;
                    if (!stats.discoveredStructures.contains(dedupKey)) {
                        JsonObject metadata = new JsonObject();
                        metadata.addProperty("structureId", structKey);
                        metadata.addProperty("coords", coordKey);
                        metadata.addProperty("name", formatStructureName(structKey));
                        InventorySnapshotService.appendActivity(server, player, "STRUCTURE_DISCOVERED", metadata);
                        ExtendedStatsManager.recordStructureDiscovered(server, player.getUUID(), dedupKey);
                        Permaworld.LOGGER.info("[Permaworld] {} ha descubierto la estructura: {}", player.getName().getString(), structKey);
                    }
                }
            } catch (Exception e) {
                Permaworld.LOGGER.warn("[Permaworld] Error detectando estructuras para {}: {}", player.getName().getString(), e.getMessage());
            }
        }

        states.put(uuid, new LastPlayerState(dimension, gameMode, alive));
    }

    private boolean hasDiscoveredStructure(MinecraftServer server, ServerPlayer player, String structureId, String coords) {
        try {
            PermaworldRecordStore store = PermaworldRecordStore.forServer(server);
            for (JsonObject record : store.readPlayerRecords(player.getUUID())) {
                if (record.has("activityType") && "STRUCTURE_DISCOVERED".equals(record.get("activityType").getAsString())) {
                    if (record.has("metadata") && record.get("metadata").isJsonObject()) {
                        JsonObject meta = record.getAsJsonObject("metadata");
                        if (meta.has("structureId") && structureId.equals(meta.get("structureId").getAsString())) {
                            if (meta.has("coords") && coords.equals(meta.get("coords").getAsString())) {
                                return true;
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private String formatStructureName(String structureId) {
        if (structureId == null) return "Structure";
        String path = structureId.contains(":") ? structureId.split(":")[1] : structureId;
        return java.util.Arrays.stream(path.split("_"))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private boolean shouldRecordPath(UUID uuid, Vec3 position) {
        Vec3 previousSample = lastPathSamples.get(uuid);
        return previousSample == null || previousSample.distanceToSqr(position) >= MIN_DISTANCE_SQUARED;
    }

    private record LastPlayerState(String dimension, String gameMode, boolean alive) {
    }
}
