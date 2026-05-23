package net.serex.permaworld.client.config;

import java.util.HashSet;
import java.util.Set;

/**
 * POJO de configuración del mod. Se serializa con Gson a {@code config/permaworld.json}.
 * Cada feature tiene su propia sub-config para mantener el archivo organizado.
 */
public class PermaworldConfig {

    public SortConfig sort = new SortConfig();
    public QuickDropConfig quickDrop = new QuickDropConfig();
    public SlotLockConfig slotLock = new SlotLockConfig();
    public TraderConfig trader = new TraderConfig();
    public HarvestConfig harvest = new HarvestConfig();

    /** Delay entre paquetes sintéticos (ms). Anti-cheat friendly. */
    public int packetDelayMs = 25;

    public static class SortConfig {
        public boolean enabled = true;
    }

    public static class QuickDropConfig {
        public boolean enabled = true;
        public int radius = 8;
    }

    public static class SlotLockConfig {
        public boolean enabled = true;
        /** Índices de slots bloqueados (referidos al inventario del jugador). */
        public Set<Integer> lockedSlots = new HashSet<>();
    }

    public static class TraderConfig {
        public boolean enabled = true;
        /** Hashes de ofertas marcadas como favoritas. */
        public Set<Integer> favoriteTradeHashes = new HashSet<>();
    }

    public static class HarvestConfig {
        public boolean enabled = true;
    }
}
