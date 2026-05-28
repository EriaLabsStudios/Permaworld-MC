package net.serex.permaworld.client.feature.quickdrop;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.debug.DebugLog;
import net.serex.permaworld.client.feature.slotlock.SlotLockManager;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lógica principal del volcado rápido (Quick Drop Stack) a contenedores.
 */
public final class QuickDropHandler {

    private static boolean isQuickDropping = false;
    private static int autoDropTimeoutTicks = 0;

    private QuickDropHandler() {
    }

    public static boolean isQuickDropping() {
        return isQuickDropping;
    }

    public static void setQuickDropping(boolean value) {
        isQuickDropping = value;
        if (value) {
            autoDropTimeoutTicks = 0;
        }
    }

    /**
     * Punto de entrada de la feature (llamada por keybind o botón).
     */
    public static void triggerQuickDrop() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            return;
        }

        // Caso 1: Si hay un contenedor abierto
        if (mc.screen instanceof AbstractContainerScreen<?>) {
            executeFromScreen();
            return;
        }

        // Caso 2: Si está mirando directamente a un cofre
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            String blockId = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(pos).getBlock()).toString();
            if (blockId.contains("chest") || blockId.contains("barrel") || blockId.contains("shulker_box")) {
                isQuickDropping = true;
                autoDropTimeoutTicks = 0;
                mc.gameMode.useItemOn(player, net.minecraft.world.InteractionHand.MAIN_HAND, blockHit);
                DebugLog.log("quickdrop", "Auto-drop al cofre bajo el cursor pos={}", pos);
                return;
            }
        }

        // Caso 3: Buscar en la caché de cofres cercanos
        scanAndDropNearbyChests(mc, player);
    }

    /**
     * Mueve objetos compatibles desde la pantalla de contenedor actual.
     */
    public static void executeFromScreen() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) return;
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null) return;

        int count = performMerge(mc, player, menu);
        if (count > 0) {
            playSatisfyingEffects(mc, player, count);
            NearbyChestTracker.trackCurrentContainer();
        } else {
            if (mc.gui != null) {
                mc.gui.setOverlayMessage(Component.translatable("permaworld.quickdrop.feedback.no_items"), false);
            }
            playFailEffects(mc);
        }
    }

    /**
     * Ejecuta el volcado automático al detectar que se abrió un cofre en modo Quick Drop.
     */
    /**
     * Tiquea el volcado automático esperando a que se sincronice el cofre.
     */
    public static void tickAutoDrop() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            isQuickDropping = false;
            autoDropTimeoutTicks = 0;
            return;
        }

        if (mc.screen instanceof AbstractContainerScreen<?> screen) {
            autoDropTimeoutTicks++;

            // Comprobar si el contenedor externo tiene algún ítem ya cargado
            boolean hasSyncedItems = false;
            AbstractContainerMenu menu = screen.getMenu();
            if (menu != null) {
                net.minecraft.world.entity.player.Inventory playerInv = mc.player.getInventory();
                for (Slot slot : menu.slots) {
                    if (slot.container != playerInv && slot.getContainerSlot() >= 0) {
                        if (!slot.getItem().isEmpty()) {
                            hasSyncedItems = true;
                            break;
                        }
                    }
                }
            }

            // Si ya hay ítems o se alcanzó el timeout (10 ticks = 500ms)
            if (hasSyncedItems || autoDropTimeoutTicks >= 10) {
                isQuickDropping = false;
                autoDropTimeoutTicks = 0;

                int count = performMerge(mc, mc.player, menu);
                if (count > 0) {
                    playSatisfyingEffects(mc, mc.player, count);
                    NearbyChestTracker.trackCurrentContainer();
                } else {
                    if (mc.gui != null) {
                        mc.gui.setOverlayMessage(Component.translatable("permaworld.quickdrop.feedback.no_items"), false);
                    }
                    playFailEffects(mc);
                }
                mc.player.closeContainer();
            }
        } else {
            autoDropTimeoutTicks++;
            // Si el cofre no se abre en 40 ticks (2 segundos) por lag extremo o fallo, cancelamos
            if (autoDropTimeoutTicks >= 40) {
                isQuickDropping = false;
                autoDropTimeoutTicks = 0;
                DebugLog.log("quickdrop", "Timeout esperando pantalla del contenedor.");
            }
        }
    }

    private static int performMerge(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu) {
        Inventory playerInv = player.getInventory();

        // Escanear los items existentes en el contenedor externo
        Set<String> containerItems = new HashSet<>();
        for (Slot slot : menu.slots) {
            if (slot.container != playerInv && slot.getContainerSlot() >= 0) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    containerItems.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
                }
            }
        }

        if (containerItems.isEmpty()) {
            return 0;
        }

        int totalMoved = 0;
        for (Slot slot : menu.slots) {
            if (slot.container == playerInv && slot.getContainerSlot() >= 0) {
                // Ignorar slots bloqueados
                if (SlotLockManager.isSlotLocked(slot)) {
                    continue;
                }
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                    if (containerItems.contains(itemId)) {
                        totalMoved += stack.getCount();
                        mc.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.QUICK_MOVE, player);
                    }
                }
            }
        }
        return totalMoved;
    }

    private static void scanAndDropNearbyChests(Minecraft mc, LocalPlayer player) {
        Map<BlockPos, Set<String>> cache = NearbyChestTracker.getCache();
        if (cache.isEmpty()) {
            if (mc.gui != null) {
                mc.gui.setOverlayMessage(Component.translatable("permaworld.quickdrop.feedback.no_chest"), false);
            }
            playFailEffects(mc);
            return;
        }

        BlockPos playerPos = player.blockPosition();
        double radius = ConfigManager.get().config().quickDrop.radius;
        BlockPos nearestPos = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Map.Entry<BlockPos, Set<String>> entry : cache.entrySet()) {
            BlockPos pos = entry.getKey();
            Set<String> items = entry.getValue();

            double distSq = playerPos.distSqr(pos);
            if (distSq <= radius * radius && distSq < nearestDistSq) {
                if (hasMatchingInventoryItems(player, items)) {
                    nearestPos = pos;
                    nearestDistSq = distSq;
                }
            }
        }

        if (nearestPos != null) {
            isQuickDropping = true;
            autoDropTimeoutTicks = 0;
            BlockHitResult hitResult = new BlockHitResult(
                    new Vec3(nearestPos.getX() + 0.5, nearestPos.getY() + 0.5, nearestPos.getZ() + 0.5),
                    Direction.UP, nearestPos, false);
            mc.gameMode.useItemOn(player, net.minecraft.world.InteractionHand.MAIN_HAND, hitResult);
            DebugLog.log("quickdrop", "Auto-drop al cofre cercano pos={}", nearestPos);
        } else {
            if (mc.gui != null) {
                mc.gui.setOverlayMessage(Component.translatable("permaworld.quickdrop.feedback.no_items"), false);
            }
            playFailEffects(mc);
        }
    }

    private static boolean hasMatchingInventoryItems(LocalPlayer player, Set<String> targetItems) {
        Inventory playerInv = player.getInventory();
        for (int i = 0; i < playerInv.getContainerSize(); i++) {
            ItemStack stack = playerInv.getItem(i);
            if (!stack.isEmpty()) {
                if (SlotLockManager.isInventorySlotLocked(i)) {
                    continue;
                }
                String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
                if (targetItems.contains(itemId)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void playSatisfyingEffects(Minecraft mc, LocalPlayer player, int count) {
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.BUNDLE_DROP_CONTENTS, 1.25F));
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.ITEM_PICKUP, 1.1F));
        }
        if (mc.gui != null) {
            mc.gui.setOverlayMessage(Component.translatable("permaworld.quickdrop.feedback.success", count), false);
        }
    }

    private static void playFailEffects(Minecraft mc) {
        if (mc.getSoundManager() != null) {
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    SoundEvents.CHEST_LOCKED, 0.85F));
        }
    }
}
