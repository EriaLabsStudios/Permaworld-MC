package net.serex.permaworld.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.debug.DebugLog;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;
import org.spongepowered.asm.mixin.Mixin;
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

    @Inject(
            method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void permaworld$slotLock$onSlotClicked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        DebugLog.log("slotlock", "slotClicked HEAD: slotId={} button={} input={} slot.container={}.",
                slotId, button, input, slot == null ? "null" : slot.container.getClass().getSimpleName());
        if (!ConfigManager.get().config().slotLock.enabled) {
            DebugLog.log("slotlock", "Feature desactivada en config; se ignora.");
            return;
        }

        int invIndex = SlotLockManager.playerInventoryIndex(slot);
        DebugLog.log("slotlock", "playerInventoryIndex={} modifierDown={}.",
                invIndex, SlotLockManager.modifierDown());
        if (invIndex < 0) return; // slot fuera del inventario del jugador → no aplica

        if (SlotLockManager.modifierDown()) {
            // ALT (o el modificador configurado) + click sobre un slot del inventario → toggle lock.
            SlotLockManager.toggle(invIndex);
            DebugLog.log("slotlock", "Toggle lock en slot inv={} (locked ahora={}).",
                    invIndex, SlotLockManager.isLocked(invIndex));
            ci.cancel();
            return;
        }

        if (SlotLockManager.isLocked(invIndex)) {
            // Bloqueado: cancelamos cualquier interacción Vanilla con el slot.
            DebugLog.log("slotlock", "Click cancelado en slot bloqueado inv={} (button={}).",
                    invIndex, button);
            ci.cancel();
        }
    }

    @Inject(
            method = "extractSlot(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/world/inventory/Slot;II)V",
            at = @At("TAIL")
    )
    private void permaworld$slotLock$onExtractSlot(GuiGraphicsExtractor extractor, Slot slot, int mouseX, int mouseY, CallbackInfo ci) {
        if (!ConfigManager.get().config().slotLock.enabled) return;

        int invIndex = SlotLockManager.playerInventoryIndex(slot);
        if (invIndex < 0) return;

        boolean modifier = SlotLockManager.modifierDown();
        boolean locked = SlotLockManager.isLocked(invIndex);

        // Feedback "modo favorito": mientras se mantiene ALT, tintamos
        // ligeramente los slots del inventario del jugador para indicar que
        // estamos en modo de marcar favoritos. Color ámbar semitransparente.
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
