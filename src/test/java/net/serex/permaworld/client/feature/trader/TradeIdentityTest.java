package net.serex.permaworld.client.feature.trader;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeIdentityTest {

    @Test
    void equivalentDescriptorsHaveSameHash() {
        TradeDescriptor first = new TradeDescriptor(
                "minecraft:emerald", 12,
                "minecraft:air", 0,
                "minecraft:diamond_pickaxe", 1
        );
        TradeDescriptor second = new TradeDescriptor(
                "minecraft:emerald", 12,
                "minecraft:air", 0,
                "minecraft:diamond_pickaxe", 1
        );

        assertEquals(TradeIdentity.hash(first), TradeIdentity.hash(second));
    }

    @Test
    void differentCostHasDifferentHash() {
        TradeDescriptor cheap = new TradeDescriptor(
                "minecraft:emerald", 12,
                "minecraft:air", 0,
                "minecraft:diamond_pickaxe", 1
        );
        TradeDescriptor expensive = new TradeDescriptor(
                "minecraft:emerald", 18,
                "minecraft:air", 0,
                "minecraft:diamond_pickaxe", 1
        );

        assertNotEquals(TradeIdentity.hash(cheap), TradeIdentity.hash(expensive));
    }

    @Test
    void differentResultHasDifferentHash() {
        TradeDescriptor pickaxe = new TradeDescriptor(
                "minecraft:emerald", 12,
                "minecraft:air", 0,
                "minecraft:diamond_pickaxe", 1
        );
        TradeDescriptor axe = new TradeDescriptor(
                "minecraft:emerald", 12,
                "minecraft:air", 0,
                "minecraft:diamond_axe", 1
        );

        assertNotEquals(TradeIdentity.hash(pickaxe), TradeIdentity.hash(axe));
    }

    @Test
    void merchantOfferHashUsesStableBaseFirstCost() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/net/serex/permaworld/client/feature/trader/TradeIdentity.java"
        ));
        String descriptorSource = source.substring(
                source.indexOf("public static TradeDescriptor descriptor"),
                source.indexOf("private static TradeDescriptor legacyDescriptor")
        );

        assertTrue(descriptorSource.contains("offer.getBaseCostA()"));
        assertFalse(descriptorSource.contains("itemId(offer.getCostA())"));
        assertFalse(descriptorSource.contains("count(offer.getCostA())"));
    }
}
