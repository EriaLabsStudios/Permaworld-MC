package net.serex.permaworld.mixin;

import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerPlayer;
import net.serex.permaworld.server.record.InventorySnapshotService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerAdvancements.class)
public abstract class PlayerAdvancementsMixin {

    @Shadow
    private ServerPlayer player;

    @Inject(method = {"award", "method_12813", "m_12812_", "m_12813_"}, at = @At("RETURN"))
    private void permaworld$recordAdvancement(AdvancementHolder advancement, String criterion,
                                              CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue()) {
            return;
        }
        JsonObject metadata = new JsonObject();
        metadata.addProperty("advancement", advancement.id().toString());
        metadata.addProperty("criterion", criterion);
        InventorySnapshotService.appendActivity(player.level().getServer(), player, "ADVANCEMENT_DONE", metadata);
    }
}
