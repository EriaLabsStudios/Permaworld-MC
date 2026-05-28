package net.serex.permaworld.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.serex.permaworld.server.record.ExtendedStatsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Inject(method = "giveExperiencePoints", at = @At("HEAD"))
    private void permaworld$onGiveXpPoints(int xp, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer serverPlayer && xp > 0) {
            ExtendedStatsManager.recordXpGained(serverPlayer.level().getServer(), serverPlayer.getUUID(), xp);
        }
    }

    @Inject(method = "giveExperienceLevels", at = @At("HEAD"))
    private void permaworld$onGiveXpLevels(int levels, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer serverPlayer && levels > 0) {
            ExtendedStatsManager.recordLevelsGained(serverPlayer.level().getServer(), serverPlayer.getUUID(), levels);
        }
    }
}
