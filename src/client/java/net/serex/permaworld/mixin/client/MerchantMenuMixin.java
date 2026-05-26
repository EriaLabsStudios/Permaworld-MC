package net.serex.permaworld.mixin.client;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {

    @Redirect(
            method = "moveFromInventoryToPaymentSlots(ILnet/minecraft/world/item/ItemStack;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/player/Inventory;getItem(I)Lnet/minecraft/world/item/ItemStack;"
            )
    )
    private ItemStack permaworld$slotLock$hideLockedItemsFromMerchant(Inventory inventory, int slot) {
        if (ConfigManager.get().config().slotLock.enabled && SlotLockManager.isInventorySlotLocked(slot)) {
            return ItemStack.EMPTY;
        }
        return inventory.getItem(slot);
    }
}
