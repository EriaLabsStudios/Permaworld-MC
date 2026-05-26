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

    @Test
    void canCheckMarkedTradesByExactMarkType() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        store.toggleLocal("villager-a", 42);
        store.toggleGlobal("villager-a", 84);

        assertTrue(store.isMarkedAs("villager-a", 42, TradeMark.LOCAL));
        assertFalse(store.isMarkedAs("villager-a", 42, TradeMark.GLOBAL));
        assertTrue(store.isMarkedAs("villager-a", 84, TradeMark.GLOBAL));
        assertFalse(store.isMarkedAs("villager-a", 84, TradeMark.LOCAL));
    }

    @Test
    void activeMarkAcceptsLegacyDynamicTradeHash() {
        PermaworldConfig.TraderConfig config = new PermaworldConfig.TraderConfig();
        config.globalFavoriteTradeHashes.add(99);
        config.localFavoriteTradeHashes.put("villager-a", new java.util.HashSet<>(java.util.Set.of(199)));
        TradeFavoriteStore store = new TradeFavoriteStore(config);

        assertEquals(TradeMark.GLOBAL, store.activeMark("villager-a", 42, 99));
        assertEquals(TradeMark.LOCAL, store.activeMark("villager-a", 142, 199));
        assertTrue(store.isMarkedAs("villager-a", 42, 99, TradeMark.GLOBAL));
        assertTrue(store.isMarkedAs("villager-a", 142, 199, TradeMark.LOCAL));
    }
}
