package net.serex.permaworld.mixin.client;

import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class InventoryMixin {

    private static final ThreadLocal<ItemStack> permaworld$addingStack = new ThreadLocal<>();

    @Shadow
    private NonNullList<ItemStack> items;

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
    private void permaworld$slotMarks$trackAddedStack(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!ConfigManager.get().config().slotLock.enabled
                || !ConfigManager.get().config().slotLock.protectPickup
                || stack.isEmpty()) {
            return;
        }

        if (slot >= 0 && !SlotLockManager.canPickupUseInventorySlot(slot, stack)) {
            cir.setReturnValue(false);
            return;
        }

        if (slot == -1) {
            permaworld$addingStack.set(stack);
        }
    }

    @Inject(method = "add(ILnet/minecraft/world/item/ItemStack;)Z", at = @At("RETURN"))
    private void permaworld$slotMarks$clearAddedStack(int slot, ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        permaworld$addingStack.remove();
    }

    @Inject(method = "getFreeSlot", at = @At("RETURN"), cancellable = true)
    private void permaworld$slotMarks$skipReservedFreeSlots(CallbackInfoReturnable<Integer> cir) {
        ItemStack adding = permaworld$addingStack.get();
        if (adding == null) {
            return;
        }

        int firstFreeSlot = cir.getReturnValue();
        if (firstFreeSlot < 0 || SlotLockManager.canPickupUseInventorySlot(firstFreeSlot, adding)) {
            return;
        }

        for (int slot = firstFreeSlot + 1; slot < items.size(); slot++) {
            if (items.get(slot).isEmpty() && SlotLockManager.canPickupUseInventorySlot(slot, adding)) {
                cir.setReturnValue(slot);
                return;
            }
        }

        cir.setReturnValue(-1);
    }
}
