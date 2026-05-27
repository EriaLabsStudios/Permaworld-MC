package net.serex.permaworld.client.feature.harvest;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.serex.permaworld.Permaworld;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.config.PermaworldConfig;
import net.serex.permaworld.client.debug.DebugLog;
import net.serex.permaworld.client.feature.FeatureModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Feature de cosecha+replante. Al hacer click derecho con una hoz compatible
 * sobre un {@link CropBlock} en su estado maduro:
 * <ol>
 *   <li>Busca cultivos maduros del mismo tipo en el área configurada para esa
 *       hoz.</li>
 *   <li>Si hay semilla compatible en el inventario, rompe cada cultivo y lo
 *       replanta usando esa semilla.</li>
 *   <li>Si no hay semilla, no se hace nada (Vanilla maneja el click).</li>
 * </ol>
 * La selección de semilla se delega a {@link CropReplanter} (lógica pura
 * testeable).
 */
public final class RightClickHarvest implements FeatureModule {

    private static final Map<String, HoeMaterial> HOE_MATERIALS = Map.of(
            "minecraft:stone_hoe", HoeMaterial.STONE,
            "minecraft:iron_hoe", HoeMaterial.IRON,
            "minecraft:diamond_hoe", HoeMaterial.DIAMOND,
            "minecraft:netherite_hoe", HoeMaterial.NETHERITE
    );

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
        HoeArea hoeArea = hoeArea(local.getMainHandItem(), ConfigManager.get().config().harvest);
        if (hoeArea == null) {
            DebugLog.log("harvest", "Cosecha ignorada: no hay hoz compatible en mano principal.");
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
            DebugLog.log("harvest", "Cultivo no soportado: {}.", cropId);
            return InteractionResult.PASS;
        }

        Inventory inv = local.getInventory();
        int seedSlot = CropReplanter.findSeedSlot(cropId, snapshotItemIds(inv));
        if (seedSlot < 0 || seedSlot >= 9) {
            DebugLog.log("harvest", "Cultivo maduro {} pero sin semilla en hotbar; click pasa a vanilla.", cropId);
            return InteractionResult.PASS;
        }
        List<BlockPos> targets = matureSupportedCropsInArea(level, pos, cropId, hoeArea.size());
        DebugLog.log("harvest", "Cosechando {} cultivo(s) desde {} con semilla del slot {}.",
                targets.size(), pos, seedSlot);

        int previousSelected = inv.getSelectedSlot();
        boolean restoreSelection = previousSelected != seedSlot;
        inv.setSelectedSlot(seedSlot);

        try {
            for (BlockPos target : targets) {
                // 1) Romper el cultivo con el flujo Vanilla de jugador. Esto
                // envía la acción al servidor; destroyBlock() solo predice en cliente.
                boolean broken = gameMode.startDestroyBlock(target, hit.getDirection());
                if (!broken) {
                    Permaworld.LOGGER.debug("startDestroyBlock devolvió false en {}", target);
                    DebugLog.log("harvest", "startDestroyBlock falló en {}; se continua con el resto.", target);
                    continue;
                }
                // Destruir localmente el bloque en el cliente para que sea AIR y el replante no falle por colisión local
                level.destroyBlock(target, true);
                
                // 2) Replantar usando useItemOn sobre la misma cara/posición.
                gameMode.useItemOn(local, InteractionHand.MAIN_HAND, hitFor(target, hit));
                DebugLog.log("harvest", "Replantado cultivo en {}.", target);
            }
        } finally {
            if (restoreSelection) {
                inv.setSelectedSlot(previousSelected);
            }
        }

        // Consumimos el evento para evitar doble interacción.
        return InteractionResult.SUCCESS;
    }

    private static HoeArea hoeArea(ItemStack stack, PermaworldConfig.HarvestConfig config) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Identifier itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        HoeMaterial material = HOE_MATERIALS.get(itemId.toString());
        if (material == null) {
            return null;
        }
        return new HoeArea(material, areaFor(material, config));
    }

    private static int areaFor(HoeMaterial material, PermaworldConfig.HarvestConfig config) {
        return switch (material) {
            case STONE -> clampArea(config.stoneHoeArea);
            case IRON -> clampArea(config.ironHoeArea);
            case DIAMOND -> clampArea(config.diamondHoeArea);
            case NETHERITE -> clampArea(config.netheriteHoeArea);
        };
    }

    private static int clampArea(int value) {
        return Math.max(1, Math.min(8, value));
    }

    private static List<BlockPos> matureSupportedCropsInArea(Level level, BlockPos origin, String expectedCropId, int size) {
        List<BlockPos> targets = new ArrayList<>();
        int minOffset = -((size - 1) / 2);
        int maxOffset = size / 2;
        for (int x = minOffset; x <= maxOffset; x++) {
            for (int z = minOffset; z <= maxOffset; z++) {
                BlockPos pos = origin.offset(x, 0, z);
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(state)) {
                    continue;
                }
                String cropId = BuiltInRegistries.BLOCK.getKey(crop).toString();
                if (expectedCropId.equals(cropId) && HarvestRegistry.isSupported(cropId)) {
                    targets.add(pos);
                }
            }
        }
        return targets;
    }

    private static BlockHitResult hitFor(BlockPos pos, BlockHitResult original) {
        return new BlockHitResult(Vec3.atCenterOf(pos), original.getDirection(), pos, original.isInside());
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

    private enum HoeMaterial {
        STONE,
        IRON,
        DIAMOND,
        NETHERITE
    }

    private record HoeArea(HoeMaterial material, int size) {
    }
}
