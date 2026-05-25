package net.serex.permaworld.client.feature.trader;

import net.serex.permaworld.client.config.PermaworldConfig;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public final class TradeFavoriteStore {

    private final PermaworldConfig.TraderConfig config;

    public TradeFavoriteStore(PermaworldConfig.TraderConfig config) {
        this.config = config;
        ensureCollections();
    }

    public boolean isGlobalFavorite(int tradeHash) {
        ensureCollections();
        return config.globalFavoriteTradeHashes.contains(tradeHash);
    }

    public boolean isLocalFavorite(String villagerKey, int tradeHash) {
        ensureCollections();
        if (villagerKey == null || villagerKey.isBlank()) {
            return false;
        }
        return config.localFavoriteTradeHashes.getOrDefault(villagerKey, Set.of()).contains(tradeHash);
    }

    public TradeMark activeMark(String villagerKey, int tradeHash) {
        if (isGlobalFavorite(tradeHash)) {
            return TradeMark.GLOBAL;
        }
        if (isLocalFavorite(villagerKey, tradeHash)) {
            return TradeMark.LOCAL;
        }
        return TradeMark.NONE;
    }

    public TradeMark toggleLocal(String villagerKey, int tradeHash) {
        ensureCollections();
        if (villagerKey == null || villagerKey.isBlank()) {
            return TradeMark.NONE;
        }
        if (isGlobalFavorite(tradeHash)) {
            return TradeMark.GLOBAL;
        }
        Set<Integer> local = config.localFavoriteTradeHashes.computeIfAbsent(villagerKey, ignored -> new HashSet<>());
        if (local.contains(tradeHash)) {
            local.remove(tradeHash);
            if (local.isEmpty()) {
                config.localFavoriteTradeHashes.remove(villagerKey);
            }
            return TradeMark.NONE;
        }
        local.add(tradeHash);
        return TradeMark.LOCAL;
    }

    public TradeMark toggleGlobal(String villagerKey, int tradeHash) {
        ensureCollections();
        if (config.globalFavoriteTradeHashes.contains(tradeHash)) {
            config.globalFavoriteTradeHashes.remove(tradeHash);
            return TradeMark.NONE;
        }
        config.globalFavoriteTradeHashes.add(tradeHash);
        removeLocal(villagerKey, tradeHash);
        return TradeMark.GLOBAL;
    }

    public boolean isMarked(String villagerKey, int tradeHash) {
        return activeMark(villagerKey, tradeHash) != TradeMark.NONE;
    }

    private void removeLocal(String villagerKey, int tradeHash) {
        if (villagerKey == null || villagerKey.isBlank()) {
            return;
        }
        Set<Integer> local = config.localFavoriteTradeHashes.get(villagerKey);
        if (local == null) {
            return;
        }
        local.remove(tradeHash);
        if (local.isEmpty()) {
            config.localFavoriteTradeHashes.remove(villagerKey);
        }
    }

    private void ensureCollections() {
        if (config.globalFavoriteTradeHashes == null) {
            config.globalFavoriteTradeHashes = new HashSet<>();
        }
        if (config.localFavoriteTradeHashes == null) {
            config.localFavoriteTradeHashes = new HashMap<>();
        }
    }
}
