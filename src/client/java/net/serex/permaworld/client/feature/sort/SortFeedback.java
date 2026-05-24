package net.serex.permaworld.client.feature.sort;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.Slot;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SortFeedback {

    private static final long FLASH_NANOS = 450_000_000L;

    private static int menuId = -1;
    private static long untilNanos = 0L;
    private static Set<Integer> highlightedSlots = Set.of();

    private SortFeedback() {
    }

    public static void show(SortMode mode, int containerId, List<Integer> menuSlotIds) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(Component.translatable("permaworld.sort.feedback." + key(mode)), false);
        }
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }

        menuId = containerId;
        highlightedSlots = Set.copyOf(menuSlotIds);
        untilNanos = System.nanoTime() + FLASH_NANOS;
    }

    public static boolean shouldHighlight(Slot slot) {
        Minecraft mc = Minecraft.getInstance();
        if (slot == null || mc.player == null || mc.player.containerMenu == null) {
            return false;
        }
        if (mc.player.containerMenu.containerId != menuId || System.nanoTime() > untilNanos) {
            return false;
        }
        return highlightedSlots.contains(slot.index);
    }

    public static List<Integer> touchedOrFallback(Set<Integer> touchedMenuSlots, List<Integer> fallbackMenuSlots) {
        if (!touchedMenuSlots.isEmpty()) {
            return List.copyOf(touchedMenuSlots);
        }
        return fallbackMenuSlots;
    }

    public static Set<Integer> newTouchedSet() {
        return new HashSet<>();
    }

    public static String key(SortMode mode) {
        return switch (mode) {
            case COUNT -> "count";
            case CATEGORY -> "category";
            case NAME -> "name";
        };
    }
}
