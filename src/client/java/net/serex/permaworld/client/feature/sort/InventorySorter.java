package net.serex.permaworld.client.feature.sort;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.serex.permaworld.Permaworld;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.debug.DebugLog;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Traduce el orden objetivo de {@link SortStrategy} en una secuencia de clicks
 * Vanilla ({@link ContainerInput#PICKUP}) sobre el menú abierto.
 * <p>
 * Solo opera sobre los slots del inventario del jugador (storage + hotbar) para evitar
 * tocar slots especiales de GUIs externas (cofres, hornos, etc.). Respeta los slots
 * marcados como bloqueados en la config.
 */
public final class InventorySorter {

    private InventorySorter() {
    }

    /**
     * Ordena el inventario del jugador en el menú actualmente abierto.
     * No hace nada si no hay jugador, menú o slots aptos.
     */
    public static void sortPlayerInventory() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || gameMode == null) {
            return;
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return;
        }

        Set<Integer> lockedInvIndices = ConfigManager.get().config().slotLock.lockedSlots;

        // Identifica los slots del menú que corresponden al inventario del jugador.
        // En Vanilla, un Slot apunta a un Container; los del jugador apuntan a la
        // Inventory del LocalPlayer.
        Inventory inv = player.getInventory();
        List<Integer> playerSlotIds = new ArrayList<>();
        List<SortableSlot> snapshot = new ArrayList<>();
        Set<Integer> lockedMenuSlots = new java.util.HashSet<>();

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container != inv) {
                continue;
            }
            int invIndex = slot.getContainerSlot();
            // Ignora armadura y offhand: solo 0..35 (hotbar + storage).
            if (invIndex < 0 || invIndex >= Inventory.INVENTORY_SIZE) {
                continue;
            }
            playerSlotIds.add(i);
            snapshot.add(toSortable(slot.getItem()));
            if (lockedInvIndices.contains(invIndex)) {
                lockedMenuSlots.add(snapshot.size() - 1);
            }
        }

        if (snapshot.isEmpty()) {
            DebugLog.log("sort", "No se detectaron slots del jugador en el menú actual.");
            return;
        }
        DebugLog.log("sort", "Detectados {} slots del jugador ({} bloqueados).",
                snapshot.size(), lockedMenuSlots.size());

        List<SortableSlot> target = SortStrategy.sort(snapshot, lockedMenuSlots);

        // Selección-sort emitiendo swaps con 3 PICKUPs por movimiento.
        // Trabajamos sobre una copia mutable de snapshot para seguir el estado lógico.
        // IMPORTANTE: no metemos sleep entre paquetes. Antes el sort hacía Thread.sleep
        // en el client thread (25ms x 3 pickups por swap), lo que congelaba el cliente
        // entero. Ahora se emiten todos los pickups seguidos en el mismo tick.
        List<SortableSlot> current = new ArrayList<>(snapshot);
        int clicks = 0;

        for (int i = 0; i < current.size(); i++) {
            if (lockedMenuSlots.contains(i)) {
                continue;
            }
            if (equalSlots(current.get(i), target.get(i))) {
                continue;
            }
            // Busca en j > i un slot que coincida con target[i] (y que no esté bloqueado).
            int j = findSource(current, target.get(i), i + 1, lockedMenuSlots);
            if (j < 0) {
                continue;
            }
            swap(gameMode, menu.containerId, playerSlotIds.get(i), playerSlotIds.get(j));
            // Refleja el swap en la copia lógica.
            SortableSlot tmp = current.get(i);
            current.set(i, current.get(j));
            current.set(j, tmp);

            clicks += 3;
        }

        Permaworld.LOGGER.debug("Inventario ordenado con {} clicks sintéticos.", clicks);
        DebugLog.log("sort", "Sort completado: {} clicks sintéticos emitidos (sin delay).", clicks);
    }

    private static int findSource(List<SortableSlot> current, SortableSlot want, int from, Set<Integer> locked) {
        for (int j = from; j < current.size(); j++) {
            if (locked.contains(j)) continue;
            if (equalSlots(current.get(j), want)) {
                return j;
            }
        }
        return -1;
    }

    private static boolean equalSlots(SortableSlot a, SortableSlot b) {
        if (a.isEmpty() && b.isEmpty()) return true;
        return a.itemId().equals(b.itemId()) && a.count() == b.count();
    }

    /**
     * Intercambia dos slots emitiendo 3 PICKUPs: pick(a) → pick(b) → pick(a).
     * Sin delay entre paquetes para no congelar el client thread.
     */
    private static void swap(MultiPlayerGameMode gameMode, int containerId, int slotA, int slotB) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) return;

        pickup(gameMode, containerId, slotA, player);
        pickup(gameMode, containerId, slotB, player);
        pickup(gameMode, containerId, slotA, player);
    }

    private static void pickup(MultiPlayerGameMode gameMode, int containerId, int slotId, LocalPlayer player) {
        // button=0 (botón izquierdo), ContainerInput.PICKUP equivale al antiguo ClickType.PICKUP.
        gameMode.handleContainerInput(containerId, slotId, 0, ContainerInput.PICKUP, player);
    }

    private static SortableSlot toSortable(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return SortableSlot.empty();
        }
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return new SortableSlot(id, stack.getCount(), stack.getMaxStackSize());
    }

}
