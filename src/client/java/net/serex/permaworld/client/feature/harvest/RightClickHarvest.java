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

    private static final java.util.Random RANDOM = new java.util.Random();
    private static boolean isProcessing = false;

    @Override
    public void onClientInit() {
        UseBlockCallback.EVENT.register(RightClickHarvest::onUseBlock);
    }

    private static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (isProcessing) {
            return InteractionResult.PASS;
        }
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
        String offHandId = BuiltInRegistries.ITEM.getKey(offHandStack.getItem()).toString();

        boolean boneMealInMain = "minecraft:bone_meal".equals(mainHandId);
        boolean boneMealInOff = "minecraft:bone_meal".equals(offHandId);

        InteractionHand boneMealHand = null;
        ItemStack hoeStack = null;

        if (boneMealInMain) {
            hoeStack = offHandStack;
            boneMealHand = InteractionHand.MAIN_HAND;
        } else if (boneMealInOff) {
            hoeStack = mainHandStack;
            boneMealHand = InteractionHand.OFF_HAND;
        }

        if (boneMealHand != null) {
            HoeArea offhandHoeArea = hoeArea(hoeStack, ConfigManager.get().config().harvest);
            if (offhandHoeArea != null) {
                BlockPos pos = hit.getBlockPos();
                List<BlockPos> targets = bonemealableBlocksInArea(level, pos, offhandHoeArea.size());
                if (!targets.isEmpty()) {
                    DebugLog.log("harvest", "Polvo de hueso en área (tamaño {}) en {} con hoz en la otra mano.", offhandHoeArea.size(), pos);
                    try {
                        isProcessing = true;
                        for (BlockPos target : targets) {
                            ItemStack currentBoneMealStack = boneMealHand == InteractionHand.MAIN_HAND ? local.getMainHandItem() : local.getOffhandItem();
                            if (currentBoneMealStack.isEmpty() || !BuiltInRegistries.ITEM.getKey(currentBoneMealStack.getItem()).toString().equals("minecraft:bone_meal")) {
                                break;
                            }
                            gameMode.useItemOn(local, boneMealHand, hitFor(target, hit));
                        }
                    } finally {
                        isProcessing = false;
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

        List<BlockPos> targets = matureSupportedCropsInArea(level, pos, cropId, hoeArea.size());
        if (targets.isEmpty()) {
            return InteractionResult.PASS;
        }

        Inventory inv = local.getInventory();

        // 1) ¿Tiene la semilla compatible directamente en la offhand?
        ItemStack offhandStack = local.getOffhandItem();
        String offhandItemId = BuiltInRegistries.ITEM.getKey(offhandStack.getItem()).toString();
        boolean hasSeedInOffhand = HarvestRegistry.seedsFor(cropId).contains(offhandItemId);

        int seedSlot = -1;
        int menuSlotId = -1;
        boolean canReplant = true;

        if (!hasSeedInOffhand) {
            // Buscamos en el inventario (0..35)
            seedSlot = CropReplanter.findSeedSlot(cropId, snapshotItemIds(inv));
            if (seedSlot < 0) {
                canReplant = false;
            } else {
                // Buscamos el slot ID del menú que corresponde a seedSlot
                for (Slot slot : local.inventoryMenu.slots) {
                    if (slot.container == inv && slot.getContainerSlot() == seedSlot) {
                        menuSlotId = slot.index;
                        break;
                    }
                }
                if (menuSlotId == -1) {
                    canReplant = false;
                }
            }
        }

        if (!canReplant) {
            DebugLog.log("harvest", "Cultivo maduro {} pero sin semilla en el inventario; solo rompiendo plantas con aviso audiovisual y partículas.", cropId);
            // Solo rompemos los cultivos sin replantar.
            for (BlockPos target : targets) {
                boolean broken = gameMode.startDestroyBlock(target, hit.getDirection());
                if (broken) {
                    level.destroyBlock(target, true);
                    spawnParticles(level, target, net.minecraft.core.particles.ParticleTypes.SMOKE, 4);
                }
            }
            // Aviso audiovisual sutil:
            level.playLocalSound(pos.getX(), pos.getY(), pos.getZ(), net.minecraft.sounds.SoundEvents.DISPENSER_FAIL, net.minecraft.sounds.SoundSource.PLAYERS, 0.4F, 1.2F, false);
            mc.gui.setOverlayMessage(net.minecraft.network.chat.Component.translatable("permaworld.harvest.no_seeds"), false);
            return InteractionResult.SUCCESS;
        }

        DebugLog.log("harvest", "Cosechando {} cultivo(s) desde {} usando offhand (hasSeedInOffhand={}, seedSlot={}, menuSlotId={}).",
                targets.size(), pos, hasSeedInOffhand, seedSlot, menuSlotId);

        // 2) Si no tiene la semilla en la offhand, la intercambiamos temporalmente con la offhand
        if (!hasSeedInOffhand) {
            gameMode.handleContainerInput(local.inventoryMenu.containerId, menuSlotId, 40, ContainerInput.SWAP, local);
        }

        try {
            isProcessing = true;
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
                spawnParticles(level, target, net.minecraft.core.particles.ParticleTypes.HAPPY_VILLAGER, 4);
                DebugLog.log("harvest", "Replantado cultivo en {}.", target);
            }
        } finally {
            isProcessing = false;
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

    private static void spawnParticles(Level level, BlockPos pos, net.minecraft.core.particles.SimpleParticleType type, int count) {
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.3;
        double z = pos.getZ() + 0.5;
        for (int i = 0; i < count; i++) {
            double rx = (RANDOM.nextDouble() - 0.5) * 0.6;
            double ry = RANDOM.nextDouble() * 0.5;
            double rz = (RANDOM.nextDouble() - 0.5) * 0.6;
            level.addParticle(type, x + rx, y + ry, z + rz, 0.0, 0.02, 0.0);
        }
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
