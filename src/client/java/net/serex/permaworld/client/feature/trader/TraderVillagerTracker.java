package net.serex.permaworld.client.feature.trader;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.serex.permaworld.client.debug.DebugLog;

public final class TraderVillagerTracker {

    private static String currentVillagerKey;

    private TraderVillagerTracker() {
    }

    public static void register() {
        UseEntityCallback.EVENT.register(TraderVillagerTracker::onUseEntity);
    }

    public static String currentVillagerKey() {
        return currentVillagerKey;
    }

    private static InteractionResult onUseEntity(Player player,
                                                 Level level,
                                                 InteractionHand hand,
                                                 Entity entity,
                                                 EntityHitResult hit) {
        if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (entity instanceof AbstractVillager) {
            currentVillagerKey = entity.getUUID().toString();
            DebugLog.log("trader", "Aldeano actual detectado: {}.", currentVillagerKey);
        }
        return InteractionResult.PASS;
    }
}
