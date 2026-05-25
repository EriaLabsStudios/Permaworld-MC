package net.serex.permaworld.client.feature.slotlock;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.debug.DebugLog;

public final class SlotPickupProtector {

    private static final int WARNING_COOLDOWN_TICKS = 20;
    private static int warningCooldown;

    private SlotPickupProtector() {
    }

    public static void tick(Minecraft client) {
        if (warningCooldown > 0) {
            warningCooldown--;
        }
        if (!ConfigManager.get().config().slotLock.enabled
                || !ConfigManager.get().config().slotLock.protectPickup) {
            return;
        }
        if (client.player == null || client.gameMode == null) {
            return;
        }
        if (client.player.getAbilities().instabuild) {
            return;
        }

        LocalPlayer player = client.player;
        Inventory inventory = player.getInventory();
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) {
            return;
        }

        for (int inventorySlot = 0; inventorySlot < Inventory.INVENTORY_SIZE; inventorySlot++) {
            ItemStack stack = inventory.getItem(inventorySlot);
            if (stack.isEmpty() || SlotLockManager.canPickupUseInventorySlot(inventorySlot, stack)) {
                continue;
            }

            int menuSlot = findMenuSlot(menu, inventory, inventorySlot);
            if (menuSlot < 0) {
                return;
            }

            DebugLog.log("slotlock", "Auto-rechazo de item {} en slot marcado {}.",
                    SlotLockManager.itemIdOf(stack), inventorySlot);
            client.gameMode.handleContainerInput(menu.containerId, menuSlot, 1, ContainerInput.THROW, player);
            warnOnce();
            return;
        }
    }

    private static int findMenuSlot(AbstractContainerMenu menu, Inventory inventory, int inventorySlot) {
        for (int slotId = 0; slotId < menu.slots.size(); slotId++) {
            Slot slot = menu.slots.get(slotId);
            if (slot.container == inventory && slot.getContainerSlot() == inventorySlot) {
                return slotId;
            }
        }
        return -1;
    }

    private static void warnOnce() {
        if (warningCooldown > 0) {
            return;
        }
        SlotLockManager.warnReservedSlot();
        warningCooldown = WARNING_COOLDOWN_TICKS;
    }
}
