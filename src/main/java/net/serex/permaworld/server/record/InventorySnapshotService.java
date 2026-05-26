package net.serex.permaworld.server.record;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.serex.permaworld.Permaworld;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class InventorySnapshotService {

    private InventorySnapshotService() {
    }

    public static JsonObject captureSnapshot(MinecraftServer server, ServerPlayer player, String reason) {
        HolderLookup.Provider registryAccess = server.registryAccess();
        JsonArray inventory = new JsonArray();
        Inventory playerInventory = player.getInventory();
        addStacks(inventory, "inventory", playerInventory.getNonEquipmentItems(), registryAccess);
        addEquipment(inventory, "armor", player, registryAccess);
        ItemStackRecordCodec.encode("offhand", 0, player.getItemBySlot(EquipmentSlot.OFFHAND), registryAccess)
                .ifPresent(inventory::add);
        addContainer(inventory, "ender_chest", player.getEnderChestInventory(), registryAccess);

        JsonObject record = basePlayerRecord("inventory_snapshot", reason, player);
        record.addProperty("health", player.getHealth());
        record.addProperty("foodLevel", player.getFoodData().getFoodLevel());
        record.addProperty("experienceLevel", player.experienceLevel);
        record.addProperty("totalExperience", player.totalExperience);
        record.addProperty("gameMode", player.gameMode.getGameModeForPlayer().getName());
        record.addProperty("itemCount", inventory.size());
        record.add("items", inventory);
        return record;
    }

    public static JsonObject activityRecord(ServerPlayer player, String type, JsonObject metadata) {
        JsonObject record = basePlayerRecord("player_activity", type, player);
        record.addProperty("activityType", type);
        record.addProperty("itemCount", 0);
        record.add("metadata", metadata == null ? new JsonObject() : metadata);
        return record;
    }

    public static void appendSnapshot(MinecraftServer server, ServerPlayer player, String reason) {
        try {
            PermaworldRecordStore.forServer(server).appendPlayerRecord(player.getUUID(), captureSnapshot(server, player, reason));
        } catch (IOException e) {
            Permaworld.LOGGER.error("No se pudo registrar snapshot {} para {}", reason, player.getName().getString(), e);
        }
    }

    public static void appendActivity(MinecraftServer server, ServerPlayer player, String type, JsonObject metadata) {
        try {
            PermaworldRecordStore.forServer(server).appendPlayerRecord(player.getUUID(), activityRecord(player, type, metadata));
        } catch (IOException e) {
            Permaworld.LOGGER.error("No se pudo registrar actividad {} para {}", type, player.getName().getString(), e);
        }
    }

    public static JsonObject serverRecord(String type, MinecraftServer server) {
        JsonObject record = new JsonObject();
        record.addProperty("schemaVersion", 1);
        record.addProperty("type", "server_activity");
        record.addProperty("id", type.toLowerCase() + "-" + UUID.randomUUID());
        record.addProperty("timestamp", Instant.now().toString());
        record.addProperty("reason", type);
        record.addProperty("playerCount", server.getPlayerList().getPlayerCount());
        return record;
    }

    private static JsonObject basePlayerRecord(String type, String reason, ServerPlayer player) {
        JsonObject record = new JsonObject();
        record.addProperty("schemaVersion", 1);
        record.addProperty("type", type);
        record.addProperty("id", reason.toLowerCase() + "-" + UUID.randomUUID());
        record.addProperty("timestamp", Instant.now().toString());
        record.addProperty("reason", reason);
        record.addProperty("playerUuid", player.getUUID().toString());
        record.addProperty("playerName", player.getName().getString());
        record.addProperty("dimension", player.level().dimension().identifier().toString());
        record.add("position", position(player.position()));
        return record;
    }

    private static JsonObject position(Vec3 position) {
        JsonObject json = new JsonObject();
        json.addProperty("x", position.x);
        json.addProperty("y", position.y);
        json.addProperty("z", position.z);
        return json;
    }

    private static void addStacks(JsonArray target, String section, List<ItemStack> stacks,
                                  HolderLookup.Provider registryAccess) {
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStackRecordCodec.encode(section, slot, stacks.get(slot), registryAccess).ifPresent(target::add);
        }
    }

    private static void addEquipment(JsonArray target, String section, ServerPlayer player,
                                     HolderLookup.Provider registryAccess) {
        EquipmentSlot[] armorSlots = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.HEAD};
        for (int slot = 0; slot < armorSlots.length; slot++) {
            ItemStackRecordCodec.encode(section, slot, player.getItemBySlot(armorSlots[slot]), registryAccess)
                    .ifPresent(target::add);
        }
    }

    private static void addContainer(JsonArray target, String section, Container container,
                                     HolderLookup.Provider registryAccess) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStackRecordCodec.encode(section, slot, container.getItem(slot), registryAccess).ifPresent(target::add);
        }
    }
}
