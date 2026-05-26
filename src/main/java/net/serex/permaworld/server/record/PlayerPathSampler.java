package net.serex.permaworld.server.record;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

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

        states.put(uuid, new LastPlayerState(dimension, gameMode, alive));
    }

    private boolean shouldRecordPath(UUID uuid, Vec3 position) {
        Vec3 previousSample = lastPathSamples.get(uuid);
        return previousSample == null || previousSample.distanceToSqr(position) >= MIN_DISTANCE_SQUARED;
    }

    private record LastPlayerState(String dimension, String gameMode, boolean alive) {
    }
}
