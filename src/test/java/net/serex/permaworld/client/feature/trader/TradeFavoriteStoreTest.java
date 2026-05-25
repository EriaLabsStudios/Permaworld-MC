package net.serex.permaworld.client.feature.trader;

import net.serex.permaworld.client.config.PermaworldConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TradeFavoriteStoreTest {

    @Test
    void localMarksAreIsolatedByVillager() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleLocal("villager-a", 42);

        assertEquals(TradeMark.LOCAL, store.activeMark("villager-a", 42));
        assertEquals(TradeMark.NONE, store.activeMark("villager-b", 42));
    }

    @Test
    void globalMarkOverridesAndRemovesLocalMark() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleLocal("villager-a", 42);
        store.toggleGlobal("villager-a", 42);

        assertEquals(TradeMark.GLOBAL, store.activeMark("villager-a", 42));
        assertTrue(config.globalFavoriteTradeHashes.contains(42));
        assertFalse(config.localFavoriteTradeHashes.getOrDefault("villager-a", java.util.Set.of()).contains(42));
    }

    @Test
    void removingGlobalDoesNotRestoreLocalMark() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleLocal("villager-a", 42);
        store.toggleGlobal("villager-a", 42);
        store.toggleGlobal("villager-a", 42);

        assertEquals(TradeMark.NONE, store.activeMark("villager-a", 42));
    }

    @Test
    void localToggleRemovesLocalMark() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleLocal("villager-a", 42);
        store.toggleLocal("villager-a", 42);

        assertEquals(TradeMark.NONE, store.activeMark("villager-a", 42));
    }

    @Test
    void localToggleIsIgnoredWhenTradeIsGlobal() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleGlobal("villager-a", 42);
        store.toggleLocal("villager-a", 42);

        assertEquals(TradeMark.GLOBAL, store.activeMark("villager-a", 42));
        assertFalse(config.localFavoriteTradeHashes.containsKey("villager-a"));
    }
}
