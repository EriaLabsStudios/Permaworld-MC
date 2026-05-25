package net.serex.permaworld.mixin.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {

    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true)
    private void permaworld$slotMarks$preventLockedDrop(boolean fullStack, CallbackInfoReturnable<Boolean> cir) {
        if (!ConfigManager.get().config().slotLock.enabled) {
            return;
        }

        LocalPlayer player = (LocalPlayer) (Object) this;
        Inventory inventory = player.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        if (SlotLockManager.isInventorySlotLocked(selectedSlot) && !inventory.getItem(selectedSlot).isEmpty()) {
            SlotLockManager.warnBlockedItem();
            cir.setReturnValue(false);
        }
    }
}
