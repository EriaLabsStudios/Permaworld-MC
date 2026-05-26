package net.serex.permaworld.mixin.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MerchantScreenMixinSignatureTest {

    @Test
    void inheritedContainerFieldsAreNotShadowedOnMerchantScreen() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/net/serex/permaworld/mixin/client/MerchantScreenMixin.java"
        ));

        assertTrue(source.contains("extends AbstractContainerScreen<MerchantMenu>"));
        assertFalse(source.contains("protected int leftPos;"));
        assertFalse(source.contains("protected int topPos;"));
        assertFalse(source.contains("protected int imageWidth;"));
        assertFalse(source.contains("protected Font font;"));
    }

    @Test
    void buyMarkedButtonsAreSplitByTradeMark() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/net/serex/permaworld/mixin/client/MerchantScreenMixin.java"
        ));

        assertFalse(source.contains("permaworld.trader.buy_marked"));
        assertTrue(source.contains("MarkedTradeBuyer.buyMarked((MerchantScreen) (Object) this, mark)"));
        assertTrue(source.contains("return TradeMark.GLOBAL;"));
        assertTrue(source.contains("return TradeMark.LOCAL;"));
        assertTrue(source.contains("Component.literal(\"G\")"));
        assertTrue(source.contains("Component.literal(\"L\")"));
    }

    @Test
    void buyMarkedButtonsUseConfigurableLayoutAndCanBeDisabled() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/net/serex/permaworld/mixin/client/MerchantScreenMixin.java"
        ));

        assertTrue(source.contains("config().trader.markedBuyButtons"));
        assertTrue(source.contains("markedBuyButtonSize"));
        assertTrue(source.contains("markedBuyButtonOffsetX"));
        assertTrue(source.contains("markedBuyButtonOffsetY"));
        assertTrue(source.contains("markedBuyButtonGap"));
        assertTrue(source.contains("permaworld$trader$buyButtonSize()"));
        assertFalse(source.contains("private static final int BUY_BUTTON_SIZE"));
        assertFalse(source.contains("private static final int BUY_BUTTON_Y"));
        assertFalse(source.contains("GLOBAL_BUY_BUTTON_OFFSET_X"));
    }
}
