package net.serex.permaworld.server.record;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class InventoryChestRestorer {

    private static final int CHEST_SIZE = 27;

    public int restore(ServerPlayer admin, JsonObject record) {
        if (!record.has("items") || !record.get("items").isJsonArray()) {
            return 0;
        }
        JsonArray items = record.getAsJsonArray("items");
        int restored = 0;
        int chestIndex = 0;
        int slotInChest = 0;
        ChestBlockEntity currentChest = null;

        for (JsonElement element : items) {
            if (!element.isJsonObject()) {
                continue;
            }
            if (currentChest == null || slotInChest >= CHEST_SIZE) {
                currentChest = createChest(admin, record, chestIndex++);
                slotInChest = 0;
            }
            ItemStack stack = ItemStackRecordCodec.decode(element.getAsJsonObject(), admin.level().getServer().registryAccess());
            if (stack.isEmpty()) {
                continue;
            }
            currentChest.setItem(slotInChest++, stack);
            currentChest.setChanged();
            restored++;
        }
        return restored;
    }

    private ChestBlockEntity createChest(ServerPlayer admin, JsonObject record, int chestIndex) {
        BlockPos pos = admin.blockPosition().relative(admin.getDirection(), chestIndex + 1);
        ServerLevel level = admin.level();
        level.setBlock(pos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ChestBlockEntity chest)) {
            throw new IllegalStateException("No se pudo crear cofre de restore en " + pos);
        }
        String playerName = string(record, "playerName", "player");
        String reason = string(record, "reason", "record");
        Component name = Component.literal("Permaworld Log " + playerName + " " + reason);
        chest.setComponents(DataComponentMap.builder().set(DataComponents.CUSTOM_NAME, name).build());
        chest.setChanged();
        return chest;
    }

    private static String string(JsonObject record, String key, String fallback) {
        if (!record.has(key) || record.get(key).isJsonNull()) {
            return fallback;
        }
        return record.get(key).getAsString();
    }
}
