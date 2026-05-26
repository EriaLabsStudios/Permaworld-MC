package net.serex.permaworld.mixin.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.world.entity.player.Inventory;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {

    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void permaworld$slotLock$preventLockedSwap(Packet<?> packet, CallbackInfo ci) {
        if (!ConfigManager.get().config().slotLock.enabled) {
            return;
        }
        if (packet instanceof ServerboundPlayerActionPacket actionPacket) {
            if (actionPacket.getAction() == ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND) {
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    int selected = mc.player.getInventory().getSelectedSlot();
                    if (SlotLockManager.isInventorySlotLocked(selected) || SlotLockManager.isInventorySlotLocked(40)) {
                        SlotLockManager.warnBlockedItem();
                        ci.cancel();
                    }
                }
            }
        }
    }
}
