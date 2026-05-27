package net.serex.permaworld.server.record;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.serex.permaworld.Permaworld;

import java.io.IOException;

public final class ServerRecordFeature {

    private final PlayerPathSampler pathSampler = new PlayerPathSampler();
    private final RecordCommands commands = new RecordCommands();

    public void register() {
        ServerPlayConnectionEvents.JOIN.register((listener, sender, server) ->
                InventorySnapshotService.appendSnapshot(server, listener.getPlayer(), "JOIN"));
        ServerPlayConnectionEvents.DISCONNECT.register((listener, server) ->
                InventorySnapshotService.appendSnapshot(server, listener.getPlayer(), "DISCONNECT"));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> appendServerRecord(server, "SERVER_STARTED"));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> appendServerRecord(server, "SERVER_STOPPING"));
        ServerTickEvents.END_SERVER_TICK.register(pathSampler::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, buildContext, selection) -> commands.register(dispatcher));
    }

    private static void appendServerRecord(MinecraftServer server, String type) {
        try {
            PermaworldRecordStore.forServer(server).appendServerRecord(InventorySnapshotService.serverRecord(type, server));
        } catch (IOException e) {
            Permaworld.LOGGER.error("No se pudo escribir registro de servidor {}", type, e);
        }
    }
}
