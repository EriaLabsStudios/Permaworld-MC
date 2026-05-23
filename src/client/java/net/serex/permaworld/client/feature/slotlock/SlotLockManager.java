package net.serex.permaworld.client.feature.slotlock;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.serex.permaworld.Permaworld;
import net.serex.permaworld.client.config.ConfigManager;

import java.util.Set;

/**
 * Estado central de Slot Lock. Mantiene el set de slots bloqueados (índices del
 * inventario del jugador 0..35) y lo persiste a través de {@link ConfigManager}.
 * <p>
 * Solo se bloquean slots del inventario propio del jugador, no slots de GUIs
 * externas (cofres, hornos, etc.).
 */
public final class SlotLockManager {

    /** Identificador de la textura del candado mostrada como overlay. */
    public static final Identifier LOCK_TEXTURE =
            Identifier.fromNamespaceAndPath(Permaworld.MOD_ID, "textures/gui/slot_lock.png");

    private SlotLockManager() {
    }

    private static Set<Integer> locked() {
        return ConfigManager.get().config().slotLock.lockedSlots;
    }

    /** ¿Está el slot {@code invIndex} bloqueado? */
    public static boolean isLocked(int invIndex) {
        return locked().contains(invIndex);
    }

    /**
     * Comprueba si un slot del menú corresponde al inventario del jugador local
     * y está bloqueado. Devuelve {@code -1} si no es un slot del jugador.
     *
     * @return el índice del inventario del jugador si aplica; -1 si no
     */
    public static int playerInventoryIndex(Slot slot) {
        if (slot == null) return -1;
        var player = Minecraft.getInstance().player;
        if (player == null) return -1;
        Inventory inv = player.getInventory();
        if (slot.container != inv) return -1;
        int idx = slot.getContainerSlot();
        if (idx < 0 || idx >= Inventory.INVENTORY_SIZE) return -1;
        return idx;
    }

    /** Alterna el estado de bloqueo del slot y persiste. */
    public static void toggle(int invIndex) {
        Set<Integer> set = locked();
        if (!set.remove(invIndex)) {
            set.add(invIndex);
        }
        ConfigManager.get().save();
    }

    /**
     * ¿Está pulsado el modificador de Slot Lock?
     * <p>
     * Por defecto se considera "pulsado" si cualquiera de las dos teclas ALT está
     * presionada. El {@code KeyMapping} en {@link net.serex.permaworld.client.keybind.Keybinds#slotLockModifier}
     * está pensado para hacer visible y reasignable este modificador en el menú
     * de controles; la detección real se hace contra GLFW para que funcione
     * de forma estable mientras hay una pantalla abierta (donde los `KeyMapping`
     * normales no se actualizan).
     */
    public static boolean modifierDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(handle, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_ALT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
}
