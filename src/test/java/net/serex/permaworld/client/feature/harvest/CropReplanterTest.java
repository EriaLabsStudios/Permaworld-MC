package net.serex.permaworld.client.feature.harvest;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CropReplanterTest {

    private static List<String> inventoryOf(String... items) {
        List<String> inv = new ArrayList<>(36);
        for (int i = 0; i < 36; i++) {
            inv.add(i < items.length ? items[i] : "");
        }
        return inv;
    }

    @Test
    void devuelveSlotDeSemillaEnHotbar() {
        // Slot 3 = wheat_seeds en hotbar.
        List<String> inv = inventoryOf("", "", "", "minecraft:wheat_seeds");

        assertEquals(3, CropReplanter.findSeedSlot("minecraft:wheat", inv));
    }

    @Test
    void prefiereHotbarSobreStorage() {
        // Storage (slot 20) y hotbar (slot 5) tienen semillas; debe elegir hotbar.
        List<String> inv = inventoryOf();
        inv.set(20, "minecraft:wheat_seeds");
        inv.set(5, "minecraft:wheat_seeds");

        assertEquals(5, CropReplanter.findSeedSlot("minecraft:wheat", inv));
    }

    @Test
    void usaStorageSiNoHayEnHotbar() {
        List<String> inv = inventoryOf();
        inv.set(27, "minecraft:carrot");

        assertEquals(27, CropReplanter.findSeedSlot("minecraft:carrots", inv));
    }

    @Test
    void devuelveMinusUnoSiNoHaySemilla() {
        List<String> inv = inventoryOf("minecraft:apple", "minecraft:stick");

        assertEquals(-1, CropReplanter.findSeedSlot("minecraft:wheat", inv));
    }

    @Test
    void devuelveMinusUnoParaCultivoNoSoportado() {
        List<String> inv = inventoryOf("minecraft:wheat_seeds");

        assertEquals(-1, CropReplanter.findSeedSlot("minecraft:sweet_berry_bush", inv));
    }

    @Test
    void distingueSemillaPorCultivo() {
        // patata para potatoes; las wheat_seeds en mismo inventario no deben usarse.
        List<String> inv = inventoryOf("minecraft:wheat_seeds", "minecraft:potato");

        assertEquals(1, CropReplanter.findSeedSlot("minecraft:potatoes", inv));
        assertEquals(0, CropReplanter.findSeedSlot("minecraft:wheat", inv));
        assertEquals(-1, CropReplanter.findSeedSlot("minecraft:carrots", inv));
    }
}
