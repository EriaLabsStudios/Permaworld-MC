package net.serex.permaworld.server.record;

import com.google.gson.JsonObject;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public final class ItemStackRecordCodec {

    private ItemStackRecordCodec() {
    }

    public static Optional<JsonObject> encode(String section, int slot, ItemStack stack,
                                              HolderLookup.Provider registryAccess) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        JsonObject json = new JsonObject();
        json.addProperty("section", section);
        json.addProperty("slot", slot);
        json.addProperty("itemId", BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        json.addProperty("count", stack.getCount());
        if (stack.getComponents().has(DataComponents.CUSTOM_NAME)) {
            String customName = stack.getHoverName().getString();
            if (!customName.isBlank()) {
                json.addProperty("customName", customName);
            }
        }
        // Minecraft 26.1.2 replaced stack.save(registryAccess) with the registry-aware ItemStack.CODEC path.
        Tag saved = ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registryAccess), stack)
                .result()
                .orElseThrow(() -> new IllegalStateException("No se pudo serializar ItemStack"));
        json.addProperty("nbt", saved.toString());
        return Optional.of(json);
    }

    public static ItemStack decode(JsonObject json, HolderLookup.Provider registryAccess) {
        if (!json.has("nbt")) {
            return ItemStack.EMPTY;
        }
        try {
            CompoundTag tag = TagParser.parseCompoundFully(json.get("nbt").getAsString());
            return ItemStack.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registryAccess), tag)
                    .result()
                    .orElse(ItemStack.EMPTY);
        } catch (CommandSyntaxException | RuntimeException e) {
            return ItemStack.EMPTY;
        }
    }
}
