package net.serex.permaworld.client.feature.trader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.debug.DebugLog;

import java.util.ArrayList;
import java.util.List;

public final class MarkedTradeBuyer {

    private static final int RESULT_SLOT = 2;
    private static final int MAX_BUYS_PER_OFFER = 64;
    private static final int SERVER_SYNC_TICKS = 2;

    private static BuySession activeSession;

    private enum Step {
        SELECT,
        TAKE
    }

    private MarkedTradeBuyer() {
    }

    public static void buyMarked(MerchantScreen screen) {
        buyMarked(screen, TradeMark.NONE);
    }

    public static void buyMarked(MerchantScreen screen, TradeMark targetMark) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) {
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
        List<Integer> markedOffers = new ArrayList<>();

        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            if (!shouldBuy(store, villagerKey, offer, targetMark)) {
                continue;
            }
            markedOffers.add(index);
        }

        if (markedOffers.isEmpty()) {
            TraderFeedback.show(Component.translatable("permaworld.trader.no_marked"));
            return;
        }

        activeSession = new BuySession(menu.containerId, markedOffers);
        tick(screen);
    }

    public static void tick(MerchantScreen screen) {
        BuySession session = activeSession;
        if (session == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null || mc.getConnection() == null) {
            activeSession = null;
            return;
        }
        if (!ConfigManager.get().config().trader.enabled) {
            activeSession = null;
            return;
        }
        if (!(mc.player.containerMenu instanceof MerchantMenu menu) || menu.containerId != session.containerId) {
            activeSession = null;
            return;
        }
        if (screen.getMenu() != menu) {
            activeSession = null;
            return;
        }
        if (session.waitTicks > 0) {
            session.waitTicks--;
            return;
        }

        if (session.offerCursor >= session.offerIndices.size()) {
            finish(session);
            return;
        }

        int index = session.offerIndices.get(session.offerCursor);
        MerchantOffers offers = menu.getOffers();
        if (index < 0 || index >= offers.size()) {
            advanceToNextOffer(session);
            return;
        }

        MerchantOffer offer = offers.get(index);
        int hash = TradeIdentity.hash(offer);
        if (offer.isOutOfStock()) {
            TraderFeedback.show(Component.translatable("permaworld.trader.stopped_stock"));
            DebugLog.log("trader", "Trade {} parado: sin stock.", hash);
            advanceToNextOffer(session);
            return;
        }

        if (session.step == Step.SELECT) {
            menu.setSelectionHint(index);
            menu.tryMoveItems(index);
            mc.getConnection().send(new ServerboundSelectTradePacket(index));
            session.step = Step.TAKE;
            session.waitTicks = SERVER_SYNC_TICKS;
            return;
        }

        ItemStack result = menu.slots.get(RESULT_SLOT).getItem();
        if (result.isEmpty()) {
            DebugLog.log("trader", "Trade {} parado: sin resultado tras preparar materiales.", hash);
            advanceToNextOffer(session);
            return;
        }

        mc.gameMode.handleContainerInput(menu.containerId, RESULT_SLOT, 0, ContainerInput.QUICK_MOVE, mc.player);
        ItemStack afterMove = menu.slots.get(RESULT_SLOT).getItem();
        if (!afterMove.isEmpty()) {
            TraderFeedback.show(Component.translatable("permaworld.trader.stopped_space"));
            finish(session);
            DebugLog.log("trader", "Trade {} parado: el resultado sigue en el slot tras quick move.", hash);
            return;
        }

        session.totalBought++;
        session.buysForCurrentOffer++;
        session.waitTicks = SERVER_SYNC_TICKS;
        if (session.buysForCurrentOffer >= MAX_BUYS_PER_OFFER) {
            advanceToNextOffer(session);
        } else {
            session.step = Step.SELECT;
        }
    }

    private static boolean shouldBuy(TradeFavoriteStore store, String villagerKey, MerchantOffer offer, TradeMark targetMark) {
        if (targetMark == null || targetMark == TradeMark.NONE) {
            return store.isMarked(villagerKey, offer);
        }
        return store.isMarkedAs(villagerKey, offer, targetMark);
    }

    private static void advanceToNextOffer(BuySession session) {
        session.offerCursor++;
        session.buysForCurrentOffer = 0;
        session.step = Step.SELECT;
        session.waitTicks = SERVER_SYNC_TICKS;
    }

    private static void finish(BuySession session) {
        activeSession = null;
        if (session.totalBought == 0) {
            TraderFeedback.show(Component.translatable("permaworld.trader.stopped_materials"));
            return;
        }
        TraderFeedback.click();
        TraderFeedback.show(Component.translatable("permaworld.trader.bought_marked", session.totalBought));
        ConfigManager.get().save();
    }

    private static final class BuySession {
        private final int containerId;
        private final List<Integer> offerIndices;
        private int offerCursor;
        private int buysForCurrentOffer;
        private int totalBought;
        private int waitTicks;
        private Step step = Step.SELECT;

        private BuySession(int containerId, List<Integer> offerIndices) {
            this.containerId = containerId;
            this.offerIndices = List.copyOf(offerIndices);
        }
    }
}
