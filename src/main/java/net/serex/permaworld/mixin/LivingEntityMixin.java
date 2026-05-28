package net.serex.permaworld.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.serex.permaworld.server.record.ExtendedStatsManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    @Inject(method = {"hurt", "damage"}, at = @At("HEAD"))
    private void permaworld$onHurt(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity victim = (LivingEntity) (Object) this;
        if (victim.level().isClientSide() || amount <= 0) {
            return;
        }

        // Caso 1: El jugador sufre daño (victim es Player)
        if (victim instanceof ServerPlayer player) {
            String sourceName = "Entorno";
            if (source.getEntity() != null) {
                sourceName = source.getEntity().getName().getString();
            } else if (source.getMsgId() != null) {
                sourceName = formatSourceMsgId(source.getMsgId());
            }

            ExtendedStatsManager.recordDamageTaken(player.level().getServer(), player.getUUID(), amount, sourceName);

            // Registrar daño por caída
            if (source.getMsgId() != null && source.getMsgId().equals("fall")) {
                ExtendedStatsManager.recordFall(player.level().getServer(), player.getUUID(), 0, amount);
            }
        }

        // Caso 2: El jugador hace daño (atacante es Player)
        if (source.getEntity() instanceof ServerPlayer attacker) {
            ExtendedStatsManager.recordDamageDealt(attacker.level().getServer(), attacker.getUUID(), amount);
        }
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"))
    private void permaworld$onFallDistance(float fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity = (LivingEntity) (Object) this;
        if (entity instanceof ServerPlayer player && fallDistance > 0) {
            ExtendedStatsManager.recordFall(player.level().getServer(), player.getUUID(), fallDistance, 0);
        }
    }

    private String formatSourceMsgId(String msgId) {
        if (msgId == null) return "Entorno";
        return java.util.Arrays.stream(msgId.split("\\."))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }
}
