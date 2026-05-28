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

    @Inject(method = "onEnchantmentPerformed", at = @At("HEAD"))
    private void permaworld$onEnchantment(ItemStack stack, int cost, CallbackInfo ci) {
        Player player = (Player) (Object) this;
        if (player instanceof ServerPlayer serverPlayer && stack != null && !stack.isEmpty()) {
            ExtendedStatsManager.recordEnchantedItem(serverPlayer.level().getServer(), serverPlayer.getUUID());

            try {
                net.minecraft.world.item.enchantment.ItemEnchantments enchantments = stack.getEnchantments();
                for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : enchantments.entrySet()) {
                    net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder = entry.getKey();
                    net.minecraft.resources.Identifier id = holder.unwrapKey()
                            .map(net.minecraft.resources.ResourceKey::identifier)
                            .orElse(null);
                    String name = id != null ? id.toString() : "unknown";
                    ExtendedStatsManager.recordEnchantment(serverPlayer.level().getServer(), serverPlayer.getUUID(), name);
                }
            } catch (Exception e) {
                net.serex.permaworld.Permaworld.LOGGER.error("[Permaworld] Error recording enchantment", e);
            }
        }
    }
}
