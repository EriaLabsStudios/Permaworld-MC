package net.serex.permaworld.mixin.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.trader.MarkedTradeBuyer;
import net.serex.permaworld.client.feature.trader.TradeFavoriteStore;
import net.serex.permaworld.client.feature.trader.TradeIdentity;
import net.serex.permaworld.client.feature.trader.TradeMark;
import net.serex.permaworld.client.feature.trader.TraderFeedback;
import net.serex.permaworld.client.feature.trader.TraderVillagerTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MerchantScreen.class)
public abstract class MerchantScreenMixin {

    private static final int VISIBLE_TRADE_ROWS = 7;
    private static final int TRADE_ROW_X = 5;
    private static final int TRADE_ROW_Y = 16;
    private static final int TRADE_ROW_WIDTH = 88;
    private static final int TRADE_ROW_HEIGHT = 20;
    private static final int STAR_SIZE = 8;
    private static final int LOCAL_STAR_OFFSET_X = -19;
    private static final int GLOBAL_STAR_OFFSET_X = -10;
    private static final int LOCAL_TINT = 0x55FFD54A;
    private static final int GLOBAL_TINT = 0x5556A8FF;
    private static final int LOCAL_STAR_COLOR = 0xFFFFD54A;
    private static final int GLOBAL_STAR_COLOR = 0xFF56A8FF;

    @Shadow
    protected int leftPos;

    @Shadow
    protected int topPos;

    @Shadow
    protected int imageWidth;

    @Shadow
    private int scrollOff;

    @Shadow
    protected Font font;

    @Inject(method = "init", at = @At("TAIL"))
    private void permaworld$trader$addBuyMarkedButton(CallbackInfo ci) {
        if (!ConfigManager.get().config().trader.enabled) {
            return;
        }
        MerchantScreen self = (MerchantScreen) (Object) this;
        Button button = Button.builder(Component.translatable("permaworld.trader.buy_marked"),
                        ignored -> MarkedTradeBuyer.buyMarked(self))
                .bounds(leftPos + imageWidth - 102, topPos + 4, 96, 20)
                .tooltip(Tooltip.create(Component.translatable("permaworld.trader.buy_marked")))
                .build();
        ((ScreenAccessor) this).permaworld$addRenderableWidget(button);
    }

    @Inject(method = "extractBackground", at = @At("TAIL"))
    private void permaworld$trader$renderRowTints(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        if (!ConfigManager.get().config().trader.enabled) {
            return;
        }
        MerchantMenu menu = ((MerchantScreen) (Object) this).getMenu();
        MerchantOffers offers = menu.getOffers();
        TradeFavoriteStore store = new TradeFavoriteStore(ConfigManager.get().config().trader);
        String villagerKey = TraderVillagerTracker.currentVillagerKey();

        for (int row = 0; row < VISIBLE_TRADE_ROWS; row++) {
            int offerIndex = scrollOff + row;
            if (offerIndex < 0 || offerIndex >= offers.size()) {
                continue;
            }
            MerchantOffer offer = offers.get(offerIndex);
            TradeMark mark = store.activeMark(villagerKey, TradeIdentity.hash(offer));
            if (mark == TradeMark.NONE) {
                continue;
            }
            int x = leftPos + TRADE_ROW_X;
            int y = topPos + TRADE_ROW_Y + row * TRADE_ROW_HEIGHT;
            extractor.fill(x, y, x + TRADE_ROW_WIDTH, y + TRADE_ROW_HEIGHT, mark == TradeMark.GLOBAL ? GLOBAL_TINT : LOCAL_TINT);
        }
    }

    @Inject(method = "extractContents", at = @At("TAIL"))
    private void permaworld$trader$renderStars(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float tickDelta, CallbackInfo ci) {
        if (!ConfigManager.get().config().trader.enabled) {
            return;
        }
        int row = hoveredRow(mouseX, mouseY);
        if (row < 0) {
            return;
        }
        MerchantOffers offers = ((MerchantScreen) (Object) this).getMenu().getOffers();
        int offerIndex = scrollOff + row;
        if (offerIndex < 0 || offerIndex >= offers.size()) {
            return;
        }

        int y = topPos + TRADE_ROW_Y + row * TRADE_ROW_HEIGHT + 6;
        int localX = leftPos + LOCAL_STAR_OFFSET_X;
        int globalX = leftPos + GLOBAL_STAR_OFFSET_X;
        extractor.text(font, "★", localX, y, LOCAL_STAR_COLOR, true);
        extractor.text(font, "★", globalX, y, GLOBAL_STAR_COLOR, true);

        if (inside(mouseX, mouseY, localX, y, STAR_SIZE, STAR_SIZE)) {
            extractor.setTooltipForNextFrame(Component.translatable("permaworld.trader.local_favorite"), mouseX, mouseY);
        } else if (inside(mouseX, mouseY, globalX, y, STAR_SIZE, STAR_SIZE)) {
            extractor.setTooltipForNextFrame(Component.translatable("permaworld.trader.global_favorite"), mouseX, mouseY);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void permaworld$trader$clickStars(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (!ConfigManager.get().config().trader.enabled || event.button() != 0) {
            return;
        }
        int row = hoveredRow((int) event.x(), (int) event.y());
        if (row < 0) {
            return;
        }
        MerchantOffers offers = ((MerchantScreen) (Object) this).getMenu().getOffers();
        int offerIndex = scrollOff + row;
        if (offerIndex < 0 || offerIndex >= offers.size()) {
            return;
        }
        int y = topPos + TRADE_ROW_Y + row * TRADE_ROW_HEIGHT + 6;
        int localX = leftPos + LOCAL_STAR_OFFSET_X;
        int globalX = leftPos + GLOBAL_STAR_OFFSET_X;
        boolean local = inside((int) event.x(), (int) event.y(), localX, y, STAR_SIZE, STAR_SIZE);
        boolean global = inside((int) event.x(), (int) event.y(), globalX, y, STAR_SIZE, STAR_SIZE);
        if (!local && !global) {
            return;
        }

        MerchantOffer offer = offers.get(offerIndex);
        int hash = TradeIdentity.hash(offer);
        String villagerKey = TraderVillagerTracker.currentVillagerKey();
        TradeFavoriteStore store = new TradeFavoriteStore(ConfigManager.get().config().trader);
        if (local) {
            if (villagerKey == null || villagerKey.isBlank()) {
                TraderFeedback.show(Component.translatable("permaworld.trader.local_unavailable"));
            } else {
                store.toggleLocal(villagerKey, hash);
                ConfigManager.get().save();
                TraderFeedback.click();
            }
        } else {
            store.toggleGlobal(villagerKey, hash);
            ConfigManager.get().save();
            TraderFeedback.click();
        }
        cir.setReturnValue(true);
    }

    private int hoveredRow(int mouseX, int mouseY) {
        int x = leftPos + TRADE_ROW_X;
        int y = topPos + TRADE_ROW_Y;
        if (!inside(mouseX, mouseY, x + LOCAL_STAR_OFFSET_X - 2, y, TRADE_ROW_WIDTH - LOCAL_STAR_OFFSET_X + 2, VISIBLE_TRADE_ROWS * TRADE_ROW_HEIGHT)) {
            return -1;
        }
        int row = (mouseY - y) / TRADE_ROW_HEIGHT;
        if (row < 0 || row >= VISIBLE_TRADE_ROWS) {
            return -1;
        }
        return row;
    }

    private static boolean inside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }
}
