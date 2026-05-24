package net.serex.permaworld.client.feature.sort;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.serex.permaworld.Permaworld;
import net.serex.permaworld.client.debug.DebugLog;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;
import net.serex.permaworld.mixin.client.AbstractContainerScreenAccessor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Traduce el orden objetivo de {@link SortStrategy} en una secuencia de clicks
 * Vanilla ({@link ContainerInput#PICKUP}) sobre el menú abierto.
 * <p>
 * <strong>Sort contextual</strong> según el slot bajo el cursor:
 * <ul>
 *   <li>Hover sobre un slot del inventario del jugador → se ordena solo el
 *       <em>storage</em> (índices 9-35), respetando la hotbar.</li>
 *   <li>Hover sobre un slot de un contenedor externo (cofre, barril, etc.) →
 *       se ordena ese contenedor entero.</li>
 *   <li>Sin hover claro → storage del jugador.</li>
 * </ul>
 * Respeta los items marcados como favoritos en {@link SlotLockManager}.
 */
public final class InventorySorter {

    private InventorySorter() {
    }

    /**
     * Entry point del feature: decide el contexto según el hover y ordena.
     */
    public static void sort() {
        sort(SortMode.NAME);
    }

    public static void sort(SortMode mode) {
        sort(mode, SortTarget.CONTEXTUAL_HOVER);
    }

    public static void sortFromButton(SortMode mode) {
        sort(mode, SortTarget.SCREEN_PRIMARY);
    }

    public static void sort(SortMode mode, SortTarget target) {
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

        Inventory playerInv = player.getInventory();
        if (target == SortTarget.SCREEN_PRIMARY) {
            Container external = primaryExternalContainer(menu, playerInv);
            if (external != null) {
                DebugLog.log("sort", "Contexto botón: contenedor externo {}.", external.getClass().getSimpleName());
                sortContainer(gameMode, menu, external, /*onlyStorage=*/ false, mode);
            } else {
                DebugLog.log("sort", "Contexto botón: storage del jugador (9-35).");
                sortContainer(gameMode, menu, playerInv, /*onlyStorage=*/ true, mode);
            }
            return;
        }

        Container hovered = hoveredContainer(mc);
        boolean sortPlayerStorage = (hovered == null) || (hovered == playerInv);

        if (sortPlayerStorage) {
            DebugLog.log("sort", "Contexto: storage del jugador (9-35).");
            sortContainer(gameMode, menu, playerInv, /*onlyStorage=*/ true, mode);
        } else {
            DebugLog.log("sort", "Contexto: contenedor externo {}.", hovered.getClass().getSimpleName());
            sortContainer(gameMode, menu, hovered, /*onlyStorage=*/ false, mode);
        }
    }

    /**
     * Devuelve el {@link Container} del slot bajo el cursor, o {@code null} si
     * no hay screen de contenedor abierta o no hay hover sobre un slot.
     */
    private static Container hoveredContainer(Minecraft mc) {
        Screen screen = mc.screen;
        if (!(screen instanceof AbstractContainerScreen<?> acs)) {
            return null;
        }
        Slot hovered = ((AbstractContainerScreenAccessor) acs).permaworld$getHoveredSlot();
        if (hovered == null) return null;
        return hovered.container;
    }

    private static Container primaryExternalContainer(AbstractContainerMenu menu, Inventory playerInv) {
        String menuName = menu.getClass().getSimpleName();
        if (menuName.contains("InventoryMenu") || menuName.contains("CraftingMenu")) {
            return null;
        }

        Map<Container, Integer> candidates = new IdentityHashMap<>();
        for (Slot slot : menu.slots) {
            if (slot.container == playerInv || slot.getContainerSlot() < 0) {
                continue;
            }
            candidates.merge(slot.container, 1, Integer::sum);
        }

        Container best = null;
        int bestSize = 0;
        for (Map.Entry<Container, Integer> entry : candidates.entrySet()) {
            int size = entry.getValue();
            if (size >= 9 && size > bestSize) {
                best = entry.getKey();
                bestSize = size;
            }
        }
        return best;
    }

    /**
     * Ordena todos los slots del {@code menu} cuyo {@code slot.container} sea
     * {@code targetContainer}. Si {@code onlyStorage} es true y el contenedor es
     * el {@link Inventory} del jugador, se limita a los índices 9..35 (excluye
     * hotbar 0..8, armadura y offhand).
     */
    private static void sortContainer(MultiPlayerGameMode gameMode,
                                      AbstractContainerMenu menu,
                                      Container targetContainer,
                                      boolean onlyStorage,
                                      SortMode mode) {
        List<Integer> menuSlotIds = new ArrayList<>();
        List<SortableSlot> snapshot = new ArrayList<>();
        Set<Integer> lockedSnapshotIdx = new HashSet<>();

        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            if (slot.container != targetContainer) continue;
            int containerSlot = slot.getContainerSlot();

            if (onlyStorage && targetContainer instanceof Inventory) {
                // Solo storage del jugador: 9..35. Excluye hotbar (0..8), armadura y offhand.
                if (containerSlot < 9 || containerSlot >= Inventory.INVENTORY_SIZE) {
                    continue;
                }
            } else if (containerSlot < 0) {
                // Slot virtual (output, etc.) — saltar por seguridad.
                continue;
            }

            menuSlotIds.add(i);
            snapshot.add(toSortable(slot.getItem()));
            // Lock por item id (favoritos): si el item del slot está marcado, no se mueve.
            if (SlotLockManager.isLocked(slot.getItem())) {
                lockedSnapshotIdx.add(snapshot.size() - 1);
            }
        }

        if (snapshot.isEmpty()) {
            DebugLog.log("sort", "No se detectaron slots aptos para ordenar.");
            return;
        }
        DebugLog.log("sort", "Detectados {} slots ({} con item favorito y por tanto bloqueados).",
                snapshot.size(), lockedSnapshotIdx.size());

        List<SortableSlot> target = SortStrategy.sort(snapshot, lockedSnapshotIdx, mode);
        Set<Integer> lockedMenuSlots = lockedSnapshotIdx; // alias para legibilidad
        List<Integer> playerSlotIds = menuSlotIds;        // alias para reutilizar el bloque inferior

        // Selección-sort emitiendo swaps con 3 PICKUPs por movimiento.
        // Trabajamos sobre una copia mutable de snapshot para seguir el estado lógico.
        // IMPORTANTE: no metemos sleep entre paquetes. Antes el sort hacía Thread.sleep
        // en el client thread (25ms x 3 pickups por swap), lo que congelaba el cliente
        // entero. Ahora se emiten todos los pickups seguidos en el mismo tick.
        List<SortableSlot> current = new ArrayList<>(snapshot);
        Set<Integer> touchedMenuSlots = SortFeedback.newTouchedSet();
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
            touchedMenuSlots.add(playerSlotIds.get(i));
            touchedMenuSlots.add(playerSlotIds.get(j));
            // Refleja el swap en la copia lógica.
            SortableSlot tmp = current.get(i);
            current.set(i, current.get(j));
            current.set(j, tmp);

            clicks += 3;
        }

        Permaworld.LOGGER.debug("Inventario ordenado con {} clicks sintéticos.", clicks);
        DebugLog.log("sort", "Sort completado: {} clicks sintéticos emitidos (sin delay).", clicks);
        SortFeedback.show(mode, menu.containerId, SortFeedback.touchedOrFallback(touchedMenuSlots, menuSlotIds));
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
