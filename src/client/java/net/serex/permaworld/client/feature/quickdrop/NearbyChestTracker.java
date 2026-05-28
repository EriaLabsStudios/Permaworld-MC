package net.serex.permaworld.client.feature.quickdrop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.serex.permaworld.client.debug.DebugLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rastrear y cachear en memoria las posiciones de los cofres/contenedores
 * abiertos por el jugador y la lista de IDs de items que contenían.
 */
public final class NearbyChestTracker {

    private static final Map<BlockPos, Set<String>> CHEST_CACHE = new HashMap<>();

    private NearbyChestTracker() {
    }

    /**
     * Guarda en caché el contenido del contenedor actualmente abierto por el jugador.
     * Asocia los items con la posición del bloque que el jugador está mirando (raycast).
     */
    public static void trackCurrentContainer() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }

        AbstractContainerMenu menu = mc.player.containerMenu;
        if (menu == null) {
            return;
        }

        // Obtener la posición del cofre usando el raycast del jugador
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult blockHit)) {
            return;
        }
        BlockPos pos = blockHit.getBlockPos();

        // Validar si el bloque es un contenedor conocido (cofre, barril, shulker, etc.)
        String blockId = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
        if (!blockId.contains("chest") && !blockId.contains("barrel") && !blockId.contains("shulker_box")) {
            return;
        }

        Inventory playerInv = mc.player.getInventory();
        Set<String> itemIds = new HashSet<>();

        for (Slot slot : menu.slots) {
            // Filtrar slots que no pertenecen al inventario del jugador
            if (slot.container != playerInv && slot.getContainerSlot() >= 0) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    itemIds.add(itemId);
                }
            }
        }

        if (!itemIds.isEmpty()) {
            CHEST_CACHE.put(pos, itemIds);
            DebugLog.log("quickdrop", "Cofre guardado en cache: pos={} items={}", pos, itemIds.size());
        }
    }

    /**
     * Devuelve una copia de la caché de cofres conocidos.
     */
    public static Map<BlockPos, Set<String>> getCache() {
        return new HashMap<>(CHEST_CACHE);
    }
}
