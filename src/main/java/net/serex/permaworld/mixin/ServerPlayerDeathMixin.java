package net.serex.permaworld.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.serex.permaworld.server.record.InventorySnapshotService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerDeathMixin {

    @Inject(method = {"die", "method_6091", "m_6667_"}, at = @At("HEAD"))
    private void permaworld$captureDeathInventory(DamageSource damageSource, CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer) (Object) this;
        InventorySnapshotService.appendSnapshot(player.level().getServer(), player, "DEATH");
        net.serex.permaworld.server.record.ExtendedStatsManager.recordDeath(player.level().getServer(), player.getUUID());
    }
}
