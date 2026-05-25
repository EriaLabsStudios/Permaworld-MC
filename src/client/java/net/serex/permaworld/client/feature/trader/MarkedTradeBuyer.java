package net.serex.permaworld.client.feature.trader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.debug.DebugLog;

public final class MarkedTradeBuyer {

    private static final int RESULT_SLOT = 2;
    private static final int MAX_BUYS_PER_OFFER = 64;

    private MarkedTradeBuyer() {
    }

    public static void buyMarked(MerchantScreen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) {
            return;
        }
        if (!ConfigManager.get().config().trader.enabled) {
            return;
        }
        if (!(mc.player.containerMenu instanceof MerchantMenu menu)) {
            return;
        }

        String villagerKey = TraderVillagerTracker.currentVillagerKey();
        TradeFavoriteStore store = new TradeFavoriteStore(ConfigManager.get().config().trader);
        MerchantOffers offers = menu.getOffers();
        int marked = 0;
        int bought = 0;

        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            int hash = TradeIdentity.hash(offer);
            if (!store.isMarked(villagerKey, hash)) {
                continue;
            }
            marked++;
            bought += buyOffer(mc, menu, index);
        }

        if (marked == 0) {
            TraderFeedback.show(Component.translatable("permaworld.trader.no_marked"));
            return;
        }
        if (bought == 0) {
            TraderFeedback.show(Component.translatable("permaworld.trader.stopped_materials"));
            return;
        }
        TraderFeedback.click();
        TraderFeedback.show(Component.translatable("permaworld.trader.bought_marked", bought));
        ConfigManager.get().save();
    }

    private static int buyOffer(Minecraft mc, MerchantMenu menu, int index) {
        int bought = 0;
        for (int guard = 0; guard < MAX_BUYS_PER_OFFER; guard++) {
            MerchantOffer offer = menu.getOffers().get(index);
            int hash = TradeIdentity.hash(offer);
            if (offer.isOutOfStock()) {
                TraderFeedback.show(Component.translatable("permaworld.trader.stopped_stock"));
                DebugLog.log("trader", "Trade {} parado: sin stock.", hash);
                break;
            }

            menu.setSelectionHint(index);
            mc.gameMode.handleInventoryButtonClick(menu.containerId, index);
            menu.tryMoveItems(index);

            ItemStack result = menu.slots.get(RESULT_SLOT).getItem();
            if (result.isEmpty()) {
                DebugLog.log("trader", "Trade {} parado: sin resultado tras preparar materiales.", hash);
                break;
            }

            mc.gameMode.handleContainerInput(menu.containerId, RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, mc.player);
            ItemStack afterMove = menu.slots.get(RESULT_SLOT).getItem();
            if (!afterMove.isEmpty()) {
                TraderFeedback.show(Component.translatable("permaworld.trader.stopped_space"));
                DebugLog.log("trader", "Trade {} parado: el resultado sigue en el slot tras quick move.", hash);
                break;
            }
            bought++;
        }
        return bought;
    }
}
