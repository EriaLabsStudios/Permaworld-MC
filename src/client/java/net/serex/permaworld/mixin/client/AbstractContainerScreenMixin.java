package net.serex.permaworld.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
import net.serex.permaworld.client.feature.quickdrop.QuickDropHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

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

    @Unique
    private Button permaworld$sortByNameButton;

    @Unique
    private Button permaworld$sortByCountButton;

    @Unique
    private Button permaworld$sortByCategoryButton;

    @Unique
    private Button permaworld$quickDropButton;

    @Unique
    private Button permaworld$draggedButton = null;
    @Unique
    private double permaworld$dragStartMouseX = 0;
    @Unique
    private double permaworld$dragStartMouseY = 0;
    @Unique
    private int permaworld$dragStartOffsetX = 0;
    @Unique
    private int permaworld$dragStartOffsetY = 0;

    @Unique
    private final Set<Integer> permaworld$dragProcessedSlots = new HashSet<>();

    @Unique
    private SlotMarkMode permaworld$dragMode;

    @Unique
    private boolean permaworld$dragShouldMark;

    @Shadow
    @Final
    protected AbstractContainerMenu menu;

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
        SlotLockManager.clearActiveMode();

        if (isCreativeInventoryScreen()) {
            return;
        }

        if (QuickDropHandler.isQuickDropping()) {
            return;
        }

        if (ConfigManager.get().config().sort.enabled) {
            addSortButtons();
        }
        if (ConfigManager.get().config().quickDrop.enabled && ConfigManager.get().config().quickDrop.showButton) {
            addQuickDropButton();
        }
        if (ConfigManager.get().config().slotLock.enabled) {
            String screenName = this.getClass().getSimpleName();
            if (screenName.equals("InventoryScreen") || screenName.equals("ChestScreen")) {
                addSlotMarkButtons();
            }
        }
    }

    @Unique
    private void addSortButtons() {
        PermaworldConfig.SortConfig sort = ConfigManager.get().config().sort;
        int buttonSize = Math.max(8, sort.buttonSize);
        int gap = Math.max(0, sort.buttonGap);

        int visibleCount = 0;
        if (sort.showSortByName) visibleCount++;
        if (sort.showSortByCount) visibleCount++;
        if (sort.showSortByCategory) visibleCount++;

        int totalWidth = visibleCount > 0 ? buttonSize * visibleCount + gap * (visibleCount - 1) : 0;
        int startX = this.leftPos + this.imageWidth - totalWidth + sort.buttonOffsetX;
        int y = sortButtonY(buttonSize, sort);

        int currentX = startX;
        if (sort.showSortByName) {
            permaworld$sortByNameButton = addSortButton(currentX, y, buttonSize, "A", SortMode.NAME);
            currentX += buttonSize + gap;
        } else {
            permaworld$sortByNameButton = null;
        }
        if (sort.showSortByCount) {
            permaworld$sortByCountButton = addSortButton(currentX, y, buttonSize, "#", SortMode.COUNT);
            currentX += buttonSize + gap;
        } else {
            permaworld$sortByCountButton = null;
        }
        if (sort.showSortByCategory) {
            permaworld$sortByCategoryButton = addSortButton(currentX, y, buttonSize, "T", SortMode.CATEGORY);
        } else {
            permaworld$sortByCategoryButton = null;
        }
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

    private Button addSortButton(int x, int y, int size, String label, SortMode mode) {
        String key = SortFeedback.key(mode);
        Button button = Button.builder(Component.literal(label), ignored -> InventorySorter.sortFromButton(mode))
                .bounds(x, y, size, size)
                .tooltip(Tooltip.create(Component.translatable("permaworld.sort.tooltip." + key)))
                .build();
        ((ScreenAccessor) this).permaworld$addRenderableWidget(button);
        return button;
    }

    @Unique
    private void addQuickDropButton() {
        PermaworldConfig.SortConfig sort = ConfigManager.get().config().sort;
        int buttonSize = Math.max(8, sort.buttonSize);
        int gap = Math.max(0, sort.buttonGap);

        int visibleCount = 0;
        if (sort.enabled) {
            if (sort.showSortByName) visibleCount++;
            if (sort.showSortByCount) visibleCount++;
            if (sort.showSortByCategory) visibleCount++;
        }

        int totalWidth = visibleCount > 0 ? (buttonSize * visibleCount + gap * (visibleCount - 1)) : 0;
        int x;
        if (visibleCount == 0 || !sort.enabled) {
            x = this.leftPos + this.imageWidth - buttonSize + sort.buttonOffsetX;
        } else {
            x = this.leftPos + this.imageWidth - totalWidth + sort.buttonOffsetX - buttonSize - gap;
        }
        int y = sortButtonY(buttonSize, sort);

        permaworld$quickDropButton = Button.builder(Component.literal("⤓"), ignored -> QuickDropHandler.executeFromScreen())
                .bounds(x, y, buttonSize, buttonSize)
                .tooltip(Tooltip.create(Component.translatable("permaworld.quickdrop.tooltip")))
                .build();
        ((ScreenAccessor) this).permaworld$addRenderableWidget(permaworld$quickDropButton);
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

        if (input == ContainerInput.QUICK_MOVE
                && ConfigManager.get().config().slotLock.protectPickup
                && permaworld$quickMoveHasNoSafeTarget(slot)) {
            DebugLog.log("slotlock", "Shift-click cancelado: no hay destino valido para {}.",
                    SlotLockManager.itemIdOf(slot.getItem()));
            SlotLockManager.warnReservedSlot();
            ci.cancel();
            return;
        }

        SlotMarkMode activeMode = SlotLockManager.activeMode();
        if (activeMode != null) {
            if (button != 0 && button != 1) {
                ci.cancel();
                return;
            }

            boolean shouldMark = button == 0;
            permaworld$beginDragBrush(activeMode, shouldMark);
            boolean changed = permaworld$applyDragBrush(slot);
            DebugLog.log("slotlock", "Modo {} sobre slot {} (mark={} changed={}).",
                    activeMode, slot.getContainerSlot(), shouldMark, changed);
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

        if (input == ContainerInput.SWAP) {
            int targetSlot = (button == 40) ? 40 : button;
            if (SlotLockManager.isInventorySlotLocked(targetSlot)) {
                DebugLog.log("slotlock", "Click SWAP cancelado: slot destino {} esta bloqueado.", targetSlot);
                SlotLockManager.warnBlockedItem();
                ci.cancel();
                return;
            }
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
            SlotLockManager.warnReservedSlot();
            ci.cancel();
        }
    }

    @Unique
    private boolean permaworld$quickMoveHasNoSafeTarget(Slot source) {
        if (!SlotLockManager.isPlayerInventorySlot(source)) {
            return false;
        }

        ItemStack stack = source.getItem();
        if (stack.isEmpty()) {
            return false;
        }

        if (permaworld$hasSafeExternalTarget(source, stack)) {
            return false;
        }

        int sourceInventorySlot = source.getContainerSlot();
        int start = sourceInventorySlot < 9 ? 9 : 0;
        int end = sourceInventorySlot < 9 ? 36 : 9;
        return !permaworld$hasSafePlayerInventoryTarget(source, stack, start, end);
    }

    @Unique
    private boolean permaworld$hasSafeExternalTarget(Slot source, ItemStack stack) {
        for (Slot target : this.menu.slots) {
            if (target == source || target.container == source.container || !target.isActive()) {
                continue;
            }
            if (permaworld$canSlotAccept(target, stack)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean permaworld$hasSafePlayerInventoryTarget(Slot source, ItemStack stack, int start, int end) {
        for (Slot target : this.menu.slots) {
            if (target == source || target.container != source.container || !target.isActive()) {
                continue;
            }
            int inventorySlot = target.getContainerSlot();
            if (inventorySlot < start || inventorySlot >= end) {
                continue;
            }
            if (permaworld$canSlotAccept(target, stack)) {
                return true;
            }
        }
        return false;
    }

    @Unique
    private boolean permaworld$canSlotAccept(Slot target, ItemStack stack) {
        if (!target.mayPlace(stack)) {
            return false;
        }

        if (SlotLockManager.isPlayerInventorySlot(target)
                && !SlotLockManager.canPickupUseInventorySlot(target.getContainerSlot(), stack)) {
            return false;
        }

        ItemStack current = target.getItem();
        if (current.isEmpty()) {
            return true;
        }
        return ItemStack.isSameItemSameComponents(current, stack)
                && current.getCount() < Math.min(current.getMaxStackSize(), target.getMaxStackSize(current));
    }

    @Inject(method = "mouseDragged(Lnet/minecraft/client/input/MouseButtonEvent;DD)Z", at = @At("HEAD"), cancellable = true)
    private void permaworld$slotLock$dragSlotMarks(MouseButtonEvent event, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (!ConfigManager.get().config().slotLock.enabled || isCreativeInventoryScreen()) {
            permaworld$endDragBrush();
            return;
        }
        SlotMarkMode activeMode = SlotLockManager.activeMode();
        if (activeMode == null || (event.button() != 0 && event.button() != 1)) {
            permaworld$endDragBrush();
            return;
        }
        if (!ConfigManager.get().config().slotLock.dragBrush) {
            cir.setReturnValue(true);
            return;
        }

        Slot hovered = permaworld$slotAt(event.x(), event.y());
        if (hovered != null && SlotLockManager.isPlayerInventorySlot(hovered)) {
            if (permaworld$dragMode == null) {
                permaworld$beginDragBrush(activeMode, event.button() == 0);
            }
            permaworld$applyDragBrush(hovered);
        }
        cir.setReturnValue(true);
    }

    @Inject(method = "mouseReleased(Lnet/minecraft/client/input/MouseButtonEvent;)Z", at = @At("HEAD"))
    private void permaworld$slotLock$releaseSlotMarkDrag(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        permaworld$endDragBrush();
    }

    @Unique
    private void permaworld$beginDragBrush(SlotMarkMode mode, boolean shouldMark) {
        if (permaworld$dragMode == mode && permaworld$dragShouldMark == shouldMark) {
            return;
        }
        permaworld$dragMode = mode;
        permaworld$dragShouldMark = shouldMark;
        permaworld$dragProcessedSlots.clear();
    }

    @Unique
    private boolean permaworld$applyDragBrush(Slot slot) {
        if (permaworld$dragMode == null || !SlotLockManager.isPlayerInventorySlot(slot)) {
            return false;
        }
        int inventorySlot = slot.getContainerSlot();
        if (!permaworld$dragProcessedSlots.add(inventorySlot)) {
            return false;
        }
        return SlotLockManager.applySlotMark(slot, permaworld$dragMode, permaworld$dragShouldMark);
    }

    @Unique
    private void permaworld$endDragBrush() {
        permaworld$dragMode = null;
        permaworld$dragShouldMark = false;
        permaworld$dragProcessedSlots.clear();
    }

    @Unique
    private Slot permaworld$slotAt(double mouseX, double mouseY) {
        for (Slot slot : this.menu.slots) {
            int x = this.leftPos + slot.x;
            int y = this.topPos + slot.y;
            if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
                return slot;
            }
        }
        return null;
    }

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void permaworld$quickdrop$cancelRender(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (QuickDropHandler.isQuickDropping()) {
            ci.cancel();
        }
    }

    @Inject(
            method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V",
            at = @At("TAIL")
    )
    private void permaworld$slotLock$onExtractSlot(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        permaworld$slotLock$updateButtonPositions();
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

    @Unique
    private void permaworld$slotLock$updateButtonPositions() {
        // 1. Reposition SlotLock Buttons
        if (ConfigManager.get().config().slotLock.enabled) {
            int size = 14;
            int gap = 2;
            int x = this.leftPos + this.imageWidth + 4;
            int y = this.topPos + this.imageHeight - size * 2 - gap - 36;

            if (permaworld$favoriteMarkButton != null) {
                permaworld$favoriteMarkButton.setX(x);
                permaworld$favoriteMarkButton.setY(y);
            }
            if (permaworld$lockMarkButton != null) {
                permaworld$lockMarkButton.setX(x);
                permaworld$lockMarkButton.setY(y + size + gap);
            }
        }

        // 2. Reposition Sort Buttons
        PermaworldConfig.SortConfig sort = ConfigManager.get().config().sort;
        int buttonSize = Math.max(8, sort.buttonSize);
        int gap = Math.max(0, sort.buttonGap);

        int visibleCount = 0;
        if (sort.enabled) {
            if (sort.showSortByName) visibleCount++;
            if (sort.showSortByCount) visibleCount++;
            if (sort.showSortByCategory) visibleCount++;
        }

        int totalWidth = visibleCount > 0 ? (buttonSize * visibleCount + gap * (visibleCount - 1)) : 0;
        int startX = this.leftPos + this.imageWidth - totalWidth + sort.buttonOffsetX;
        int y = sortButtonY(buttonSize, sort);

        if (sort.enabled) {
            int currentX = startX;
            if (sort.showSortByName && permaworld$sortByNameButton != null) {
                permaworld$sortByNameButton.setX(currentX);
                permaworld$sortByNameButton.setY(y);
                currentX += buttonSize + gap;
            }
            if (sort.showSortByCount && permaworld$sortByCountButton != null) {
                permaworld$sortByCountButton.setX(currentX);
                permaworld$sortByCountButton.setY(y);
                currentX += buttonSize + gap;
            }
            if (sort.showSortByCategory && permaworld$sortByCategoryButton != null) {
                permaworld$sortByCategoryButton.setX(currentX);
                permaworld$sortByCategoryButton.setY(y);
            }
        }

        // 3. Reposition Quick Drop Button
        if (ConfigManager.get().config().quickDrop.enabled && ConfigManager.get().config().quickDrop.showButton) {
            int x;
            if (visibleCount == 0 || !sort.enabled) {
                x = this.leftPos + this.imageWidth - buttonSize + sort.buttonOffsetX;
            } else {
                x = startX - buttonSize - gap;
            }

            if (permaworld$quickDropButton != null) {
                permaworld$quickDropButton.setX(x);
                permaworld$quickDropButton.setY(y);
            }
        }
    }

    @Unique
    private boolean permaworld$isMouseOverButton(Button btn, double mouseX, double mouseY) {
        if (btn == null || !btn.visible) return false;
        return mouseX >= btn.getX() && mouseX < btn.getX() + btn.getWidth() &&
               mouseY >= btn.getY() && mouseY < btn.getY() + btn.getHeight();
    }

    @Unique
    private boolean permaworld$isPlayerInventoryScreen() {
        String screenName = this.getClass().getSimpleName();
        return screenName.equals("InventoryScreen");
    }

    @Unique
    private int permaworld$getYOffset(PermaworldConfig.SortConfig sort) {
        return permaworld$isPlayerInventoryScreen() ? sort.inventoryButtonOffsetY : sort.containerButtonOffsetY;
    }

    @Unique
    private void permaworld$setYOffset(PermaworldConfig.SortConfig sort, int val) {
        if (permaworld$isPlayerInventoryScreen()) {
            sort.inventoryButtonOffsetY = val;
        } else {
            sort.containerButtonOffsetY = val;
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void permaworld$layoutDrag$onClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        PermaworldConfig.SortConfig sort = ConfigManager.get().config().sort;
        if (!sort.enabled || !sort.layoutEditMode || event.button() != 0) {
            return;
        }

        Button clicked = null;
        if (permaworld$isMouseOverButton(permaworld$sortByNameButton, event.x(), event.y())) {
            clicked = permaworld$sortByNameButton;
        } else if (permaworld$isMouseOverButton(permaworld$sortByCountButton, event.x(), event.y())) {
            clicked = permaworld$sortByCountButton;
        } else if (permaworld$isMouseOverButton(permaworld$sortByCategoryButton, event.x(), event.y())) {
            clicked = permaworld$sortByCategoryButton;
        } else if (permaworld$isMouseOverButton(permaworld$quickDropButton, event.x(), event.y())) {
            clicked = permaworld$quickDropButton;
        }

        if (clicked != null) {
            permaworld$draggedButton = clicked;
            permaworld$dragStartMouseX = event.x();
            permaworld$dragStartMouseY = event.y();
            permaworld$dragStartOffsetX = sort.buttonOffsetX;
            permaworld$dragStartOffsetY = permaworld$getYOffset(sort);
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseDragged", at = @At("HEAD"), cancellable = true)
    private void permaworld$layoutDrag$onDrag(MouseButtonEvent event, double dragX, double dragY, CallbackInfoReturnable<Boolean> cir) {
        if (permaworld$draggedButton != null) {
            PermaworldConfig.SortConfig sort = ConfigManager.get().config().sort;
            double dx = event.x() - permaworld$dragStartMouseX;
            double dy = event.y() - permaworld$dragStartMouseY;

            sort.buttonOffsetX = permaworld$dragStartOffsetX + (int) dx;
            permaworld$setYOffset(sort, permaworld$dragStartOffsetY + (int) dy);

            // Update on screen instantly
            permaworld$slotLock$updateButtonPositions();
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"))
    private void permaworld$layoutDrag$onRelease(MouseButtonEvent event, CallbackInfoReturnable<Boolean> cir) {
        if (permaworld$draggedButton != null) {
            permaworld$draggedButton = null;
            ConfigManager.get().save(); // Persist final coordinates
        }
    }
}
