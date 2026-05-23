package net.serex.permaworld.client.feature.slotlock;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.serex.permaworld.Permaworld;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.keybind.KeyInput;
import net.serex.permaworld.client.keybind.Keybinds;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/**
 * Estado central de Slot Lock. El lock se aplica por <strong>item id</strong>
 * (ej. {@code "minecraft:diamond"}), no por índice de slot. Esto permite que
 * los favoritos sobrevivan a:
 * <ul>
 *   <li>Reordenaciones (sort) del inventario.</li>
 *   <li>El menú de Creativo, donde los slots no apuntan al Inventory del jugador
 *       en la mayoría de pestañas.</li>
 *   <li>Mover el item entre hotbar/storage.</li>
 * </ul>
 * Persistido vía {@link ConfigManager}.
 */
public final class SlotLockManager {

    /** Identificador de la textura del candado mostrada como overlay. */
    public static final Identifier LOCK_TEXTURE =
            Identifier.fromNamespaceAndPath(Permaworld.MOD_ID, "textures/gui/slot_lock.png");

    private SlotLockManager() {
    }

    private static Set<String> locked() {
        return ConfigManager.get().config().slotLock.lockedItems;
    }

    /** Devuelve el item id del stack, o {@code null} si está vacío. */
    public static String itemIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** ¿Está el item del stack bloqueado? */
    public static boolean isLocked(ItemStack stack) {
        String id = itemIdOf(stack);
        return id != null && locked().contains(id);
    }

    /** ¿Está el item id (puede ser null) bloqueado? */
    public static boolean isLockedId(String itemId) {
        return itemId != null && locked().contains(itemId);
    }

    /**
     * ¿El slot tiene un item bloqueado? Slots vacíos devuelven false.
     */
    public static boolean isSlotLocked(Slot slot) {
        if (slot == null) return false;
        return isLocked(slot.getItem());
    }

    /**
     * Alterna el lock del item presente en el slot y persiste.
     * Si el slot está vacío, no hace nada.
     *
     * @return el item id sobre el que se hizo toggle, o {@code null} si el slot estaba vacío.
     */
    public static String toggle(Slot slot) {
        if (slot == null) return null;
        String id = itemIdOf(slot.getItem());
        if (id == null) return null;
        Set<String> set = locked();
        if (!set.remove(id)) {
            set.add(id);
        }
        ConfigManager.get().save();
        return id;
    }

    /**
     * Comprueba si el jugador local existe. Útil para no procesar clicks sin contexto.
     */
    public static boolean hasPlayer() {
        return Minecraft.getInstance().player != null;
    }

    /**
     * ¿Está pulsado el modificador de Slot Lock?
     * <p>
     * Usa el {@code KeyMapping} visible en el menú de controles y lo consulta
     * contra GLFW para que funcione de forma estable mientras hay una pantalla
     * abierta (donde los {@code KeyMapping} normales no se actualizan).
     */
    public static boolean modifierDown() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return modifierDown(code -> GLFW.glfwGetKey(handle, code) == GLFW.GLFW_PRESS);
    }

    static boolean modifierDown(java.util.function.IntPredicate keyDown) {
        if (Keybinds.slotLockModifier == null) {
            return altFallbackDown(keyDown);
        }
        try {
            return KeyInput.isKeyboardKeyDown(Keybinds.slotLockModifier.saveString(), keyDown);
        } catch (Exception e) {
            return altFallbackDown(keyDown);
        }
    }

    private static boolean altFallbackDown(java.util.function.IntPredicate keyDown) {
        return keyDown.test(GLFW.GLFW_KEY_LEFT_ALT) || keyDown.test(GLFW.GLFW_KEY_RIGHT_ALT);
    }
}
