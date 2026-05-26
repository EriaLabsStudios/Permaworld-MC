package net.serex.permaworld.mixin.client;

import net.minecraft.world.Container;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {

    @Redirect(
            method = "moveFromInventoryToPaymentSlot",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/Container;getItem(I)Lnet/minecraft/world/item/ItemStack;"
            ),
            require = 0
    )
    private ItemStack permaworld$slotLock$hideLockedItemsFromMerchant(Container inventory, int slot) {
        if (ConfigManager.get().config().slotLock.enabled && SlotLockManager.isInventorySlotLocked(slot)) {
            return ItemStack.EMPTY;
        }
        return inventory.getItem(slot);
    }
}
