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
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
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

        ItemStack mainHandStack = local.getMainHandItem();
        ItemStack offHandStack = local.getOffhandItem();
        String mainHandId = BuiltInRegistries.ITEM.getKey(mainHandStack.getItem()).toString();

        if ("minecraft:bone_meal".equals(mainHandId)) {
            HoeArea offhandHoeArea = hoeArea(offHandStack, ConfigManager.get().config().harvest);
            if (offhandHoeArea != null) {
                BlockPos pos = hit.getBlockPos();
                List<BlockPos> targets = bonemealableBlocksInArea(level, pos, offhandHoeArea.size());
                if (!targets.isEmpty()) {
                    DebugLog.log("harvest", "Polvo de hueso en área (tamaño {}) en {} con hoz en mano secundaria.", offhandHoeArea.size(), pos);
                    for (BlockPos target : targets) {
                        if (local.getMainHandItem().isEmpty() || !BuiltInRegistries.ITEM.getKey(local.getMainHandItem().getItem()).toString().equals("minecraft:bone_meal")) {
                            break;
                        }
                        gameMode.useItemOn(local, InteractionHand.MAIN_HAND, hitFor(target, hit));
                    }
                    return InteractionResult.SUCCESS;
                }
            }
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

        // 1) ¿Tiene la semilla compatible directamente en la offhand?
        ItemStack offhandStack = local.getOffhandItem();
        String offhandItemId = BuiltInRegistries.ITEM.getKey(offhandStack.getItem()).toString();
        boolean hasSeedInOffhand = HarvestRegistry.seedsFor(cropId).contains(offhandItemId);

        int seedSlot = -1;
        int menuSlotId = -1;

        if (!hasSeedInOffhand) {
            // Buscamos en el inventario (0..35)
            seedSlot = CropReplanter.findSeedSlot(cropId, snapshotItemIds(inv));
            if (seedSlot < 0) {
                DebugLog.log("harvest", "Cultivo maduro {} pero sin semilla en el inventario; click pasa a vanilla.", cropId);
                return InteractionResult.PASS;
            }

            // Buscamos el slot ID del menú que corresponde a seedSlot
            for (Slot slot : local.inventoryMenu.slots) {
                if (slot.container == inv && slot.getContainerSlot() == seedSlot) {
                    menuSlotId = slot.index;
                    break;
                }
            }

            if (menuSlotId == -1) {
                DebugLog.log("harvest", "Error: no se pudo mapear seedSlot {} a menuSlotId.", seedSlot);
                return InteractionResult.PASS;
            }
        }

        List<BlockPos> targets = matureSupportedCropsInArea(level, pos, cropId, hoeArea.size());
        if (targets.isEmpty()) {
            return InteractionResult.PASS;
        }

        DebugLog.log("harvest", "Cosechando {} cultivo(s) desde {} usando offhand (hasSeedInOffhand={}, seedSlot={}, menuSlotId={}).",
                targets.size(), pos, hasSeedInOffhand, seedSlot, menuSlotId);

        // 2) Si no tiene la semilla en la offhand, la intercambiamos temporalmente con la offhand
        if (!hasSeedInOffhand) {
            gameMode.handleContainerInput(local.inventoryMenu.containerId, menuSlotId, 40, ContainerInput.SWAP, local);
        }

        try {
            for (BlockPos target : targets) {
                // Romper el cultivo con el flujo Vanilla de jugador.
                boolean broken = gameMode.startDestroyBlock(target, hit.getDirection());
                if (!broken) {
                    Permaworld.LOGGER.debug("startDestroyBlock devolvió false en {}", target);
                    DebugLog.log("harvest", "startDestroyBlock falló en {}; se continua con el resto.", target);
                    continue;
                }
                // Destruir localmente el bloque en el cliente para que sea AIR y el replante no falle por colisión local
                level.destroyBlock(target, true);

                // Replantar usando la offhand (InteractionHand.OFF_HAND).
                gameMode.useItemOn(local, InteractionHand.OFF_HAND, hitFor(target, hit));
                DebugLog.log("harvest", "Replantado cultivo en {}.", target);
            }
        } finally {
            // 3) Devolver la semilla a su sitio si hicimos swap
            if (!hasSeedInOffhand) {
                gameMode.handleContainerInput(local.inventoryMenu.containerId, menuSlotId, 40, ContainerInput.SWAP, local);
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

    private static List<BlockPos> bonemealableBlocksInArea(Level level, BlockPos origin, int size) {
        List<BlockPos> targets = new ArrayList<>();
        int minOffset = -((size - 1) / 2);
        int maxOffset = size / 2;
        for (int x = minOffset; x <= maxOffset; x++) {
            for (int z = minOffset; z <= maxOffset; z++) {
                BlockPos pos = origin.offset(x, 0, z);
                BlockState state = level.getBlockState(pos);
                if (state.getBlock() instanceof net.minecraft.world.level.block.BonemealableBlock bonemealable) {
                    if (bonemealable.isValidBonemealTarget(level, pos, state)) {
                        targets.add(pos);
                    }
                }
            }
        }
        return targets;
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
