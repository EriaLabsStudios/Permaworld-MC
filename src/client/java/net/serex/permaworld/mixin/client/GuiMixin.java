package net.serex.permaworld.mixin.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager.SlotMark;
import net.serex.permaworld.client.feature.slotlock.SlotMarkRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
public abstract class GuiMixin {

    @Inject(
            method = "extractItemHotbar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("TAIL")
    )
    private void permaworld$slotMarks$renderHotbar(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ConfigManager.get().config().slotLock.enabled) {
            return;
        }

        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        Inventory inventory = player.getInventory();
        int left = extractor.guiWidth() / 2 - 91;
        int y = extractor.guiHeight() - 19;
        for (int slot = 0; slot < 9; slot++) {
            SlotMark mark = SlotLockManager.markForInventorySlot(slot);
            if (mark == null) {
                continue;
            }
            int x = left + 3 + slot * 20;
            if (inventory.getItem(slot).isEmpty() && mark.itemId() != null) {
                extractor.fill(x, y, x + 16, y + 16, 0x55000000);
            }
            SlotMarkRenderer.renderIcon(extractor, x, y, mark.mode());
        }
    }
}
