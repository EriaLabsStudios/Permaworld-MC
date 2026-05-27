package net.serex.permaworld.server.web;

import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.serex.permaworld.server.record.InventoryChestRestorer;
import net.serex.permaworld.server.record.PermaworldRecordStore;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

public final class WebRestoreService {

    private final MinecraftServer server;
    private final PermaworldRecordStore store;

    public WebRestoreService(MinecraftServer server) {
        this(server, PermaworldRecordStore.forServer(server));
    }

    WebRestoreService(MinecraftServer server, PermaworldRecordStore store) {
        this.server = server;
        this.store = store;
    }

    public JsonObject restore(String adminName, UUID playerId, String recordId) throws IOException {
        ServerPlayer admin = server.getPlayerList().getPlayerByName(adminName);
        if (admin == null || !isAdmin(admin)) {
            return WebDtos.restoreResult(false, "Admin no valido o sin permisos.", 0);
        }
        Optional<JsonObject> record = store.findPlayerRecord(playerId, recordId);
        if (record.isEmpty()) {
            return WebDtos.restoreResult(false, "Registro no encontrado.", 0);
        }
        if (!"inventory_snapshot".equals(record.get().has("type") ? record.get().get("type").getAsString() : "")) {
            return WebDtos.restoreResult(false, "Ese registro no se puede restaurar.", 0);
        }
        int restored = new InventoryChestRestorer().restore(admin, record.get());
        return WebDtos.restoreResult(true, "Cofre(s) creados correctamente.", restored);
    }

    private boolean isAdmin(ServerPlayer player) {
        NameAndId nameAndId = new NameAndId(player.getGameProfile());
        return server.isSingleplayerOwner(nameAndId) || server.getPlayerList().isOp(nameAndId);
    }
}
