package net.serex.permaworld.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.config.PermaworldConfig;
import net.serex.permaworld.client.debug.DebugLog;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager.SlotMark;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager.SlotMarkMode;
import net.serex.permaworld.client.feature.slotlock.SlotMarkRenderer;
import net.serex.permaworld.client.feature.sort.InventorySorter;
import net.serex.permaworld.client.feature.sort.SortFeedback;
import net.serex.permaworld.client.feature.sort.SortMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin que añade las marcas de slot a cualquier pantalla con contenedor:
 * <ul>
 *   <li>Dos botones laterales activan modo Favorito o Lock.</li>
 *   <li>Los clicks sobre slots del inventario del jugador guardan una marca
 *       persistente por índice de slot.</li>
 *   <li>Al final de {@code extractSlot} se pinta la marca o el item fantasma.</li>
 * </ul>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

    @Unique
    private Button permaworld$favoriteMarkButton;

    @Unique
    private Button permaworld$lockMarkButton;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected int imageWidth;

    @Shadow
    protected int imageHeight;

    @Inject(method = "init", at = @At("TAIL"))
    private void permaworld$sort$addButtons(CallbackInfo ci) {
        if (isCreativeInventoryScreen()) {
            SlotLockManager.clearActiveMode();
            return;
        }

        if (ConfigManager.get().config().sort.enabled) {
            addSortButtons();
        }
        if (ConfigManager.get().config().slotLock.enabled) {
            addSlotMarkButtons();
        }
    }

    @Unique
    private void addSortButtons() {
        PermaworldConfig.SortConfig sort = ConfigManager.get().config().sort;
        int buttonSize = Math.max(8, sort.buttonSize);
        int gap = Math.max(0, sort.buttonGap);
        int totalWidth = buttonSize * 3 + gap * 2;
        int x = this.leftPos + this.imageWidth - totalWidth + sort.buttonOffsetX;
        int y = sortButtonY(buttonSize, sort);

        addSortButton(x, y, buttonSize, "A", SortMode.NAME);
        addSortButton(x + buttonSize + gap, y, buttonSize, "#", SortMode.COUNT);
        addSortButton(x + (buttonSize + gap) * 2, y, buttonSize, "T", SortMode.CATEGORY);
    }

    @Unique
    private void addSlotMarkButtons() {
        int size = 14;
        int gap = 2;
        int x = this.leftPos + this.imageWidth + 4;
        int y = this.topPos + this.imageHeight - size * 2 - gap - 36;

        permaworld$favoriteMarkButton = addMarkButton(
                x, y, size, SlotMarkMode.FAVORITE,
                "★", "permaworld.slotmark.tooltip.favorite");
        permaworld$lockMarkButton = addMarkButton(
                x, y + size + gap, size, SlotMarkMode.LOCK,
                "L", "permaworld.slotmark.tooltip.lock");
        updateSlotMarkButtonLabels();
    }

    @Unique
    private Button addMarkButton(int x, int y, int size, SlotMarkMode mode, String label, String tooltipKey) {
        Button button = Button.builder(markButtonLabel(mode, label), ignored -> {
                    SlotLockManager.toggleActiveMode(mode);
                    updateSlotMarkButtonLabels();
                })
                .bounds(x, y, size, size)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey)))
                .build();
        ((ScreenAccessor) this).permaworld$addRenderableWidget(button);
        return button;
    }

    @Unique
    private void updateSlotMarkButtonLabels() {
        if (permaworld$favoriteMarkButton != null) {
            permaworld$favoriteMarkButton.setMessage(markButtonLabel(SlotMarkMode.FAVORITE, "★"));
        }
        if (permaworld$lockMarkButton != null) {
            permaworld$lockMarkButton.setMessage(markButtonLabel(SlotMarkMode.LOCK, "L"));
        }
    }

    @Unique
    private Component markButtonLabel(SlotMarkMode mode, String label) {
        return Component.literal(SlotLockManager.isActiveMode(mode) ? "[" + label + "]" : label);
    }

    private int sortButtonY(int buttonSize, PermaworldConfig.SortConfig sort) {
        String screenName = ((Object) this).getClass().getSimpleName();
        int y;
        if (screenName.contains("InventoryScreen")
                || screenName.contains("CraftingScreen")) {
            y = this.topPos + sort.inventoryButtonOffsetY;
        } else {
            y = this.topPos + sort.containerButtonOffsetY;
        }
        return Math.min(y, this.topPos + this.imageHeight - buttonSize - 6);
    }

    private boolean isCreativeInventoryScreen() {
        return ((Object) this).getClass().getSimpleName().contains("CreativeModeInventoryScreen");
    }

    private void addSortButton(int x, int y, int size, String label, SortMode mode) {
        String key = SortFeedback.key(mode);
        Button button = Button.builder(Component.literal(label), ignored -> InventorySorter.sortFromButton(mode))
                .bounds(x, y, size, size)
                .tooltip(Tooltip.create(Component.translatable("permaworld.sort.tooltip." + key)))
                .build();
        ((ScreenAccessor) this).permaworld$addRenderableWidget(button);
    }

    @Inject(
            method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void permaworld$slotLock$onSlotClicked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        DebugLog.log("slotlock", "slotClicked HEAD: slotId={} button={} input={} slot.container={}.",
                slotId, button, input, slot == null ? "null" : (slot.container == null ? "null" : slot.container.getClass().getSimpleName()));
        if (!ConfigManager.get().config().slotLock.enabled) {
            DebugLog.log("slotlock", "Feature desactivada en config; se ignora.");
            return;
        }
        if (isCreativeInventoryScreen()) {
            return;
        }
        if (slot == null) return;

        SlotMarkMode activeMode = SlotLockManager.activeMode();
        if (activeMode != null) {
            boolean toggled = SlotLockManager.toggleSlotMark(slot, activeMode);
            DebugLog.log("slotlock", "Modo {} sobre slot {} (toggled={}).",
                    activeMode, slot.getContainerSlot(), toggled);
            ci.cancel();
            return;
        }

        boolean modifier = SlotLockManager.modifierDown();
        String itemId = SlotLockManager.itemIdOf(slot.getItem());
        DebugLog.log("slotlock", "itemId={} modifierDown={}.", itemId, modifier);

        if (modifier) {
            boolean toggled = SlotLockManager.toggleSlotMark(slot, SlotMarkMode.LOCK);
            DebugLog.log("slotlock", "ALT toggle lock sobre slot {} (toggled={}).",
                    slot.getContainerSlot(), toggled);
            ci.cancel();
            return;
        }

        if (SlotLockManager.isSlotLocked(slot)) {
            // Lock: cancelamos cualquier interacción Vanilla con el slot.
            DebugLog.log("slotlock", "Click cancelado en slot con item bloqueado item={} (button={}).",
                    itemId, button);
            ci.cancel();
            return;
        }

        ItemStack carried = SlotLockManager.hasPlayer()
                ? net.minecraft.client.Minecraft.getInstance().player.containerMenu.getCarried()
                : ItemStack.EMPTY;
        if (!SlotLockManager.canPlaceInReservedSlot(slot, carried)) {
            DebugLog.log("slotlock", "Click cancelado: slot favorito reservado para otro item.");
            ci.cancel();
        }
    }

    @Inject(
            method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V",
            at = @At("TAIL")
    )
    private void permaworld$slotLock$onExtractSlot(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (slot == null) return;

        if (ConfigManager.get().config().sort.enabled && SortFeedback.shouldHighlight(slot)) {
            extractor.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x55A5D6FF);
        }

        if (!ConfigManager.get().config().slotLock.enabled) return;
        if (isCreativeInventoryScreen()) return;

        boolean modifier = SlotLockManager.modifierDown();
        SlotMark mark = SlotLockManager.markForSlot(slot);

        if (SlotLockManager.activeMode() != null && SlotLockManager.isPlayerInventorySlot(slot)) {
            int color = SlotLockManager.activeMode() == SlotMarkMode.LOCK ? 0x402A7FFF : 0x40FFC400;
            extractor.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
        } else if (modifier && SlotLockManager.isPlayerInventorySlot(slot)) {
            extractor.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x30FFC400);
        }

        if (mark == null) return;

        ItemStack ghost = SlotLockManager.ghostStack(slot);
        if (!ghost.isEmpty()) {
            extractor.fakeItem(ghost, slot.x, slot.y);
            extractor.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x99000000);
        }

        SlotMarkRenderer.renderIcon(extractor, slot.x, slot.y, mark.mode());
    }
}
