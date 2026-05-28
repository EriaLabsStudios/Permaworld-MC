package net.serex.permaworld.mixin;

import net.minecraft.advancements.criterion.EnchantedItemTrigger;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.serex.permaworld.server.record.ExtendedStatsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnchantedItemTrigger.class)
public abstract class EnchantedItemTriggerMixin {

    @Inject(method = "trigger", at = @At("HEAD"))
    private void permaworld$onEnchantedItemTrigger(ServerPlayer player, ItemStack stack, int levels, CallbackInfo ci) {
        if (player != null && stack != null && !stack.isEmpty()) {
            ExtendedStatsManager.recordEnchantedItem(player.level().getServer(), player.getUUID());

            try {
                net.minecraft.world.item.enchantment.ItemEnchantments enchantments = stack.getEnchantments();
                if (enchantments == null || enchantments.isEmpty()) {
                    enchantments = stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS, net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
                }
                for (java.util.Map.Entry<net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>, Integer> entry : enchantments.entrySet()) {
                    net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment> holder = entry.getKey();
                    net.minecraft.resources.Identifier id = holder.unwrapKey()
                            .map(net.minecraft.resources.ResourceKey::identifier)
                            .orElse(null);
                    String name = id != null ? id.toString() : "unknown";
                    ExtendedStatsManager.recordEnchantment(player.level().getServer(), player.getUUID(), name);
                }
            } catch (Exception e) {
                net.serex.permaworld.Permaworld.LOGGER.error("[Permaworld] Error recording enchantment in trigger", e);
            }
        }
    }
}
