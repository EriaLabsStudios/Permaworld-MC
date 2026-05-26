package net.serex.permaworld.client.feature.trader;

import net.serex.permaworld.client.config.PermaworldConfig;
import net.minecraft.world.item.trading.MerchantOffer;

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

    public boolean isGlobalFavorite(int stableTradeHash, int legacyTradeHash) {
        ensureCollections();
        return config.globalFavoriteTradeHashes.contains(stableTradeHash)
                || config.globalFavoriteTradeHashes.contains(legacyTradeHash);
    }

    public boolean isLocalFavorite(String villagerKey, int tradeHash) {
        ensureCollections();
        if (villagerKey == null || villagerKey.isBlank()) {
            return false;
        }
        return config.localFavoriteTradeHashes.getOrDefault(villagerKey, Set.of()).contains(tradeHash);
    }

    public boolean isLocalFavorite(String villagerKey, int stableTradeHash, int legacyTradeHash) {
        ensureCollections();
        if (villagerKey == null || villagerKey.isBlank()) {
            return false;
        }
        Set<Integer> local = config.localFavoriteTradeHashes.getOrDefault(villagerKey, Set.of());
        return local.contains(stableTradeHash) || local.contains(legacyTradeHash);
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

    public TradeMark activeMark(String villagerKey, MerchantOffer offer) {
        return activeMark(villagerKey, TradeIdentity.hash(offer), TradeIdentity.legacyHash(offer));
    }

    public TradeMark activeMark(String villagerKey, int stableTradeHash, int legacyTradeHash) {
        if (isGlobalFavorite(stableTradeHash, legacyTradeHash)) {
            return TradeMark.GLOBAL;
        }
        if (isLocalFavorite(villagerKey, stableTradeHash, legacyTradeHash)) {
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

    public TradeMark toggleLocal(String villagerKey, MerchantOffer offer) {
        return toggleLocal(villagerKey, TradeIdentity.hash(offer), TradeIdentity.legacyHash(offer));
    }

    public TradeMark toggleLocal(String villagerKey, int stableTradeHash, int legacyTradeHash) {
        ensureCollections();
        if (villagerKey == null || villagerKey.isBlank()) {
            return TradeMark.NONE;
        }
        if (isGlobalFavorite(stableTradeHash, legacyTradeHash)) {
            return TradeMark.GLOBAL;
        }
        Set<Integer> local = config.localFavoriteTradeHashes.computeIfAbsent(villagerKey, ignored -> new HashSet<>());
        if (local.contains(stableTradeHash) || local.contains(legacyTradeHash)) {
            local.remove(stableTradeHash);
            local.remove(legacyTradeHash);
            if (local.isEmpty()) {
                config.localFavoriteTradeHashes.remove(villagerKey);
            }
            return TradeMark.NONE;
        }
        local.add(stableTradeHash);
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

    public TradeMark toggleGlobal(String villagerKey, MerchantOffer offer) {
        return toggleGlobal(villagerKey, TradeIdentity.hash(offer), TradeIdentity.legacyHash(offer));
    }

    public TradeMark toggleGlobal(String villagerKey, int stableTradeHash, int legacyTradeHash) {
        ensureCollections();
        if (config.globalFavoriteTradeHashes.contains(stableTradeHash)
                || config.globalFavoriteTradeHashes.contains(legacyTradeHash)) {
            config.globalFavoriteTradeHashes.remove(stableTradeHash);
            config.globalFavoriteTradeHashes.remove(legacyTradeHash);
            return TradeMark.NONE;
        }
        config.globalFavoriteTradeHashes.add(stableTradeHash);
        removeLocal(villagerKey, stableTradeHash);
        removeLocal(villagerKey, legacyTradeHash);
        return TradeMark.GLOBAL;
    }

    public boolean isMarked(String villagerKey, int tradeHash) {
        return activeMark(villagerKey, tradeHash) != TradeMark.NONE;
    }

    public boolean isMarked(String villagerKey, MerchantOffer offer) {
        return activeMark(villagerKey, offer) != TradeMark.NONE;
    }

    public boolean isMarkedAs(String villagerKey, int tradeHash, TradeMark mark) {
        return activeMark(villagerKey, tradeHash) == mark;
    }

    public boolean isMarkedAs(String villagerKey, MerchantOffer offer, TradeMark mark) {
        return activeMark(villagerKey, offer) == mark;
    }

    public boolean isMarkedAs(String villagerKey, int stableTradeHash, int legacyTradeHash, TradeMark mark) {
        return activeMark(villagerKey, stableTradeHash, legacyTradeHash) == mark;
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
