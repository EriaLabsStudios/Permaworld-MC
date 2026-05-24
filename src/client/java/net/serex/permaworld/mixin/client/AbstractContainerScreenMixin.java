package net.serex.permaworld.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.config.PermaworldConfig;
import net.serex.permaworld.client.debug.DebugLog;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;
import net.serex.permaworld.client.feature.sort.InventorySorter;
import net.serex.permaworld.client.feature.sort.SortFeedback;
import net.serex.permaworld.client.feature.sort.SortMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin que añade la lógica de Slot Lock a cualquier pantalla con contenedor:
 * <ul>
 *   <li>Si el jugador mantiene el modificador (por defecto ALT) y clickea un
 *       slot del inventario, se alterna el lock de ese slot.</li>
 *   <li>Si el slot está bloqueado y no se pulsa el modificador, el click se
 *       cancela: el item no se mueve.</li>
 *   <li>Al final de {@code extractSlot} se pinta una textura de candado sobre
 *       los slots bloqueados.</li>
 * </ul>
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin {

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
        if (!ConfigManager.get().config().sort.enabled) {
            return;
        }
        if (isCreativeInventoryScreen()) {
            return;
        }

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
        if (slot == null) return;

        boolean modifier = SlotLockManager.modifierDown();
        String itemId = SlotLockManager.itemIdOf(slot.getItem());
        DebugLog.log("slotlock", "itemId={} modifierDown={}.", itemId, modifier);

        if (modifier) {
            // ALT + click → toggle lock del item que haya en el slot.
            // No restringimos al Inventory del jugador: así también funciona en
            // Creativo (donde los slots no apuntan a player.getInventory()) y
            // sobre cofres/contenedores externos para marcar el item como favorito.
            String toggled = SlotLockManager.toggle(slot);
            DebugLog.log("slotlock", "Toggle lock sobre item={} (locked ahora={}).",
                    toggled, SlotLockManager.isLockedId(toggled));
            ci.cancel();
            return;
        }

        if (SlotLockManager.isSlotLocked(slot)) {
            // Bloqueado: cancelamos cualquier interacción Vanilla con el slot.
            DebugLog.log("slotlock", "Click cancelado en slot con item bloqueado item={} (button={}).",
                    itemId, button);
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

        boolean modifier = SlotLockManager.modifierDown();
        boolean locked = SlotLockManager.isSlotLocked(slot);

        // Feedback "modo favorito": mientras se mantiene ALT, tintamos
        // ligeramente todos los slots para indicar que estamos en modo de
        // marcar/desmarcar favoritos. Color ámbar semitransparente.
        if (modifier) {
            extractor.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x40FFC400);
        }

        if (!locked) return;

        // Estrella pequeña (8x8) en la esquina superior derecha del slot.
        // La textura es 16x16 nativa; la sobrecarga de blit con uWidth/vHeight
        // permite escalarla al tamaño deseado.
        RenderPipeline pipeline = RenderPipelines.GUI_TEXTURED;
        int size = 8;
        int x = slot.x + 16 - size + 1; // +1 para que sobresalga un poco a la derecha
        int y = slot.y - 1;             // -1 para que sobresalga un poco arriba
        extractor.blit(pipeline, SlotLockManager.LOCK_TEXTURE,
                x, y, 0.0F, 0.0F, size, size, 16, 16, 16, 16);
    }
}
