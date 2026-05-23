package net.serex.permaworld.client.feature.harvest;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.serex.permaworld.Permaworld;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.FeatureModule;

import java.util.ArrayList;
import java.util.List;

/**
 * Feature de cosecha+replante automático. Al hacer click derecho sobre un
 * {@link CropBlock} en su estado maduro:
 * <ol>
 *   <li>Si hay semilla compatible en el inventario, se rompe el bloque y se
 *       replanta automáticamente usando esa semilla.</li>
 *   <li>Si no hay semilla, no se hace nada (Vanilla maneja el click).</li>
 * </ol>
 * La selección de semilla se delega a {@link CropReplanter} (lógica pura
 * testeable).
 */
public final class RightClickHarvest implements FeatureModule {

    @Override
    public void onClientInit() {
        UseBlockCallback.EVENT.register(RightClickHarvest::onUseBlock);
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!ConfigManager.get().config().harvest.enabled) {
            return InteractionResult.PASS;
        }
        // Solo lado cliente y solo con el LocalPlayer.
        if (level.isClientSide() == false || hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer local = mc.player;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (local == null || gameMode == null || player != local) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CropBlock crop)) {
            return InteractionResult.PASS;
        }
        if (!crop.isMaxAge(state)) {
            return InteractionResult.PASS;
        }

        String cropId = BuiltInRegistries.BLOCK.getKey(crop).toString();
        if (!HarvestRegistry.isSupported(cropId)) {
            return InteractionResult.PASS;
        }

        Inventory inv = local.getInventory();
        int seedSlot = CropReplanter.findSeedSlot(cropId, snapshotItemIds(inv));
        if (seedSlot < 0) {
            // Sin semilla → dejamos pasar el click vanilla (no rompemos nada).
            return InteractionResult.PASS;
        }

        // Necesitamos que la semilla esté en la mano principal para que
        // useItemOn la coloque. Si está fuera de la hotbar, hacemos pick.
        int previousSelected = inv.getSelectedSlot();
        boolean restoreSelection = false;
        if (seedSlot < 9) {
            inv.setSelectedSlot(seedSlot);
            restoreSelection = previousSelected != seedSlot;
        } else {
            // Pickea el slot al hotbar actual (equivalente a tecla "swap").
            inv.pickSlot(seedSlot);
            restoreSelection = false; // pickSlot ya gestiona la selección
        }

        try {
            // 1) Romper el cultivo (el servidor se encarga del drop).
            boolean broken = gameMode.destroyBlock(pos);
            if (!broken) {
                Permaworld.LOGGER.debug("destroyBlock devolvió false en {}", pos);
                return InteractionResult.PASS;
            }
            // 2) Replantar usando useItemOn sobre la misma cara/posición.
            gameMode.useItemOn(local, InteractionHand.MAIN_HAND, hit);
        } finally {
            if (restoreSelection) {
                inv.setSelectedSlot(previousSelected);
            }
        }

        // Consumimos el evento para evitar doble interacción.
        return InteractionResult.SUCCESS;
    }

    private static List<String> snapshotItemIds(Inventory inv) {
        List<String> out = new ArrayList<>(Inventory.INVENTORY_SIZE);
        for (int i = 0; i < Inventory.INVENTORY_SIZE; i++) {
            ItemStack s = inv.getItem(i);
            if (s == null || s.isEmpty()) {
                out.add("");
            } else {
                out.add(BuiltInRegistries.ITEM.getKey(s.getItem()).toString());
            }
        }
        return out;
    }
}
