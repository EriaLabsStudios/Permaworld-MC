package net.serex.permaworld.client.feature.trader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarkedTradeBuyerFlowTest {

    @Test
    void merchantTradesUseVanillaSelectPacketAndTickedTakeFlow() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/net/serex/permaworld/client/feature/trader/MarkedTradeBuyer.java"
        ));

        assertTrue(source.contains("ServerboundSelectTradePacket"));
        assertTrue(source.contains("public static void tick(MerchantScreen screen)"));
        assertTrue(source.contains("private enum Step"));
        assertFalse(source.contains("handleInventoryButtonClick"));
    }
}
