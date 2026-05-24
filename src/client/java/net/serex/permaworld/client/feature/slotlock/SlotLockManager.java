package net.serex.permaworld.client.feature.slotlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.serex.permaworld.Permaworld;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.config.PermaworldConfig;
import net.serex.permaworld.client.keybind.KeyInput;
import net.serex.permaworld.client.keybind.Keybinds;
import org.lwjgl.glfw.GLFW;

import java.util.Map;
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

    private static SlotMarkMode activeMode = null;

    private SlotLockManager() {
    }

    private static Set<String> locked() {
        return ConfigManager.get().config().slotLock.lockedItems;
    }

    private static Map<Integer, PermaworldConfig.SlotLockConfig.SlotMarkConfig> slotMarks() {
        if (ConfigManager.get().config().slotLock.playerSlots == null) {
            ConfigManager.get().config().slotLock.playerSlots = new java.util.HashMap<>();
        }
        return ConfigManager.get().config().slotLock.playerSlots;
    }

    /** Devuelve el item id del stack, o {@code null} si está vacío. */
    public static String itemIdOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    public static SlotMarkMode activeMode() {
        return activeMode;
    }

    public static void toggleActiveMode(SlotMarkMode mode) {
        activeMode = activeMode == mode ? null : mode;
    }

    public static void clearActiveMode() {
        activeMode = null;
    }

    public static boolean isActiveMode(SlotMarkMode mode) {
        return activeMode == mode;
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
        SlotMark mark = markForSlot(slot);
        return mark != null && mark.mode() == SlotMarkMode.LOCK;
    }

    public static boolean isSlotFavorite(Slot slot) {
        SlotMark mark = markForSlot(slot);
        return mark != null && mark.mode() == SlotMarkMode.FAVORITE;
    }

    public static boolean isMarked(Slot slot) {
        return markForSlot(slot) != null;
    }

    public static SlotMark markForSlot(Slot slot) {
        if (!isPlayerInventorySlot(slot)) {
            return null;
        }
        return markForInventorySlot(slot.getContainerSlot());
    }

    public static SlotMark markForInventorySlot(int inventorySlot) {
        PermaworldConfig.SlotLockConfig.SlotMarkConfig raw = slotMarks().get(inventorySlot);
        if (raw == null) {
            return null;
        }
        SlotMarkMode mode = SlotMarkMode.fromConfig(raw.mode);
        if (mode == null) {
            return null;
        }
        return new SlotMark(mode, raw.itemId);
    }

    public static boolean isPlayerInventorySlot(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (slot == null || mc.player == null) {
            return false;
        }
        return slot.container == mc.player.getInventory()
                && slot.getContainerSlot() >= 0
                && slot.getContainerSlot() < Inventory.INVENTORY_SIZE;
    }

    public static boolean toggleSlotMark(Slot slot, SlotMarkMode mode) {
        if (!isPlayerInventorySlot(slot)) {
            return false;
        }

        int inventorySlot = slot.getContainerSlot();
        SlotMark previous = markForInventorySlot(inventorySlot);
        String itemId = itemIdOf(slot.getItem());
        if (itemId == null && previous != null) {
            itemId = previous.itemId();
        }

        if (previous != null && previous.mode() == mode) {
            slotMarks().remove(inventorySlot);
            ConfigManager.get().save();
            play(mode);
            return true;
        }

        if (mode == SlotMarkMode.FAVORITE && itemId == null) {
            return false;
        }

        PermaworldConfig.SlotLockConfig.SlotMarkConfig raw = new PermaworldConfig.SlotLockConfig.SlotMarkConfig();
        raw.mode = mode.configName();
        raw.itemId = itemId;
        slotMarks().put(inventorySlot, raw);
        ConfigManager.get().save();
        play(mode);
        return true;
    }

    public static boolean canPlaceInReservedSlot(Slot slot, ItemStack carried) {
        SlotMark mark = markForSlot(slot);
        if (mark == null || mark.mode() != SlotMarkMode.FAVORITE) {
            return true;
        }
        if (!slot.getItem().isEmpty()) {
            return true;
        }
        String reserved = mark.itemId();
        String carriedId = itemIdOf(carried);
        return reserved == null || carriedId == null || reserved.equals(carriedId);
    }

    public static ItemStack ghostStack(Slot slot) {
        SlotMark mark = markForSlot(slot);
        if (mark == null || mark.itemId() == null || !slot.getItem().isEmpty()) {
            return ItemStack.EMPTY;
        }
        try {
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(mark.itemId()));
            if (item == null) {
                return ItemStack.EMPTY;
            }
            return new ItemStack(item);
        } catch (Exception ignored) {
            return ItemStack.EMPTY;
        }
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

    private static void play(SlotMarkMode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getSoundManager() == null) {
            return;
        }
        if (mode == SlotMarkMode.LOCK) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.CHEST_LOCKED, 0.75F));
        } else {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.45F));
        }
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

    public enum SlotMarkMode {
        FAVORITE("favorite"),
        LOCK("lock");

        private final String configName;

        SlotMarkMode(String configName) {
            this.configName = configName;
        }

        public String configName() {
            return configName;
        }

        public static SlotMarkMode fromConfig(String value) {
            if ("lock".equals(value)) {
                return LOCK;
            }
            if ("favorite".equals(value)) {
                return FAVORITE;
            }
            return null;
        }
    }

    public record SlotMark(SlotMarkMode mode, String itemId) {
    }
}
