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
import net.serex.permaworld.client.feature.slotlock.SlotLockManager.SlotMark;
import net.serex.permaworld.mixin.client.AbstractContainerScreenAccessor;

import java.util.ArrayList;
import java.util.HashMap;
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

    private static class MergeableSlot {
        final int menuSlotId;
        ItemStack virtualStack;

        MergeableSlot(int menuSlotId, ItemStack stack) {
            this.menuSlotId = menuSlotId;
            this.virtualStack = stack.copy();
        }
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
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        // 1. Recopilar todos los slots y estados de marcas
        List<Integer> menuSlotIds = new ArrayList<>();
        List<MergeableSlot> mergeable = new ArrayList<>();
        Map<Integer, SlotMark> slotMarks = new HashMap<>();

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
            SlotMark mark = SlotLockManager.markForSlot(slot);
            if (mark != null) {
                slotMarks.put(i, mark);
            }

            boolean isLocked = mark != null && mark.mode() == SlotLockManager.SlotMarkMode.LOCK;
            if (!isLocked) {
                mergeable.add(new MergeableSlot(i, slot.getItem()));
            }
        }

        if (menuSlotIds.isEmpty()) {
            DebugLog.log("sort", "No se detectaron slots aptos para ordenar.");
            return;
        }

        // 2. Ejecutar la fusión virtual de stacks y emitir los clicks físicos en un tick
        int n = mergeable.size();
        int mergeClicks = 0;
        for (int i = 0; i < n; i++) {
            MergeableSlot slotA = mergeable.get(i);
            if (slotA.virtualStack.isEmpty() || slotA.virtualStack.getCount() >= slotA.virtualStack.getMaxStackSize()) {
                continue;
            }

            for (int j = i + 1; j < n; j++) {
                MergeableSlot slotB = mergeable.get(j);
                if (slotB.virtualStack.isEmpty()) {
                    continue;
                }

                if (ItemStack.isSameItemSameComponents(slotA.virtualStack, slotB.virtualStack)) {
                    int maxStack = slotA.virtualStack.getMaxStackSize();
                    int space = maxStack - slotA.virtualStack.getCount();
                    if (space <= 0) {
                        break;
                    }

                    // Enviar los 3 clicks de fusión: B al cursor -> A -> B sobrante
                    pickup(gameMode, menu.containerId, slotB.menuSlotId, player);
                    pickup(gameMode, menu.containerId, slotA.menuSlotId, player);
                    pickup(gameMode, menu.containerId, slotB.menuSlotId, player);
                    mergeClicks += 3;

                    // Actualizar estado virtual
                    int toMove = Math.min(space, slotB.virtualStack.getCount());
                    slotA.virtualStack.setCount(slotA.virtualStack.getCount() + toMove);
                    slotB.virtualStack.setCount(slotB.virtualStack.getCount() - toMove);
                    if (slotB.virtualStack.getCount() <= 0) {
                        slotB.virtualStack = ItemStack.EMPTY;
                    }

                    if (slotA.virtualStack.getCount() >= maxStack) {
                        break;
                    }
                }
            }
        }

        if (mergeClicks > 0) {
            DebugLog.log("sort", "Fusión de stacks: emitidos {} clicks sintéticos.", mergeClicks);
        }

        // 3. Mapear los stacks virtuales post-fusión
        Map<Integer, ItemStack> finalStacks = new HashMap<>();
        for (MergeableSlot ms : mergeable) {
            finalStacks.put(ms.menuSlotId, ms.virtualStack);
        }
        for (int menuSlotId : menuSlotIds) {
            if (!finalStacks.containsKey(menuSlotId)) {
                finalStacks.put(menuSlotId, menu.slots.get(menuSlotId).getItem());
            }
        }

        // 4. Construir instantánea de ordenación a partir de los stacks ya fusionados
        List<SortableSlot> snapshot = new ArrayList<>();
        Set<Integer> markedSnapshotIdx = new HashSet<>();
        Set<Integer> immovableSnapshotIdx = new HashSet<>();
        Map<Integer, SlotMark> marksBySnapshotIdx = new HashMap<>();

        for (int i = 0; i < menuSlotIds.size(); i++) {
            int menuSlotId = menuSlotIds.get(i);
            ItemStack stack = finalStacks.get(menuSlotId);
            snapshot.add(toSortable(stack));

            SlotMark mark = slotMarks.get(menuSlotId);
            if (mark != null) {
                int snapshotIdx = i;
                markedSnapshotIdx.add(snapshotIdx);
                marksBySnapshotIdx.put(snapshotIdx, mark);
                if (mark.mode() == SlotLockManager.SlotMarkMode.LOCK) {
                    immovableSnapshotIdx.add(snapshotIdx);
                }
            }
        }

        DebugLog.log("sort", "Fusión completada. slots={} (reservados={}).",
                snapshot.size(), markedSnapshotIdx.size());

        List<SortableSlot> target = sortRespectingSlotMarks(snapshot, marksBySnapshotIdx, mode);
        List<Integer> playerSlotIds = menuSlotIds;

        // 5. Bucle de swaps
        List<SortableSlot> current = new ArrayList<>(snapshot);
        Set<Integer> touchedMenuSlots = SortFeedback.newTouchedSet();
        int clicks = 0;

        for (int i = 0; i < current.size(); i++) {
            if (immovableSnapshotIdx.contains(i)) {
                continue;
            }
            if (equalSlots(current.get(i), target.get(i))) {
                continue;
            }
            int j = findSource(current, target.get(i), i + 1, markedSnapshotIdx);
            if (j < 0) {
                continue;
            }
            swap(gameMode, menu.containerId, playerSlotIds.get(i), playerSlotIds.get(j));
            touchedMenuSlots.add(playerSlotIds.get(i));
            touchedMenuSlots.add(playerSlotIds.get(j));
            
            SortableSlot tmp = current.get(i);
            current.set(i, current.get(j));
            current.set(j, tmp);

            clicks += 3;
        }

        Permaworld.LOGGER.debug("Inventario ordenado con {} clicks sintéticos.", clicks + mergeClicks);
        DebugLog.log("sort", "Sort completado: {} clicks de ordenación, {} clicks de fusión.", clicks, mergeClicks);
        SortFeedback.show(mode, menu.containerId, SortFeedback.touchedOrFallback(touchedMenuSlots, menuSlotIds));
    }

    private static List<SortableSlot> sortRespectingSlotMarks(List<SortableSlot> current,
                                                              Map<Integer, SlotMark> marksBySnapshotIdx,
                                                              SortMode mode) {
        int size = current.size();
        SortableSlot[] target = new SortableSlot[size];
        Set<Integer> used = new HashSet<>();
        Set<Integer> markedIndices = marksBySnapshotIdx.keySet();

        for (Map.Entry<Integer, SlotMark> entry : marksBySnapshotIdx.entrySet()) {
            int targetIdx = entry.getKey();
            SlotMark mark = entry.getValue();
            SortableSlot currentSlot = current.get(targetIdx);

            if (mark.mode() == SlotLockManager.SlotMarkMode.LOCK) {
                target[targetIdx] = currentSlot;
                used.add(targetIdx);
                continue;
            }

            String reservedItemId = mark.itemId();
            if (reservedItemId == null) {
                target[targetIdx] = SortableSlot.empty();
                continue;
            }

            if (!currentSlot.isEmpty() && currentSlot.itemId().equals(reservedItemId)) {
                target[targetIdx] = currentSlot;
                used.add(targetIdx);
                continue;
            }

            int sourceIdx = findFirstUnusedItem(current, reservedItemId, used, markedIndices);
            if (sourceIdx >= 0) {
                target[targetIdx] = current.get(sourceIdx);
                used.add(sourceIdx);
            } else {
                target[targetIdx] = SortableSlot.empty();
            }
        }

        List<SortableSlot> movable = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (used.contains(i) || markedIndices.contains(i)) {
                continue;
            }
            SortableSlot slot = current.get(i);
            if (!slot.isEmpty()) {
                movable.add(slot);
            }
        }

        movable = SortStrategy.sort(movable, Set.of(), mode);
        int movableIdx = 0;
        for (int i = 0; i < size; i++) {
            if (target[i] != null) {
                continue;
            }
            target[i] = movableIdx < movable.size() ? movable.get(movableIdx++) : SortableSlot.empty();
        }

        return List.of(target);
    }

    private static int findFirstUnusedItem(List<SortableSlot> current,
                                           String itemId,
                                           Set<Integer> used,
                                           Set<Integer> markedIndices) {
        for (int i = 0; i < current.size(); i++) {
            if (used.contains(i) || markedIndices.contains(i)) {
                continue;
            }
            SortableSlot slot = current.get(i);
            if (!slot.isEmpty() && slot.itemId().equals(itemId)) {
                return i;
            }
        }
        return -1;
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
