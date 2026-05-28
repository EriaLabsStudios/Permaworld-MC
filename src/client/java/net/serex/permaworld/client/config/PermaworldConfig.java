package net.serex.permaworld.client.config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
    public ResourcePackConfig resourcePack = new ResourcePackConfig();

    /** Delay entre paquetes sintéticos (ms). Anti-cheat friendly. */
    public int packetDelayMs = 25;

    /**
     * Modo debug. Cuando está activo, las features emiten logs detallados con
     * prefijo {@code [Permaworld][debug]} para diagnosticar problemas in-game
     * (detección de teclas, clicks de slot, harvests, etc.).
     */
    public boolean debug = false;

    public static class SortConfig {
        public static final int DEFAULT_BUTTON_SIZE = 10;
        public static final int DEFAULT_BUTTON_GAP = 2;
        public static final int DEFAULT_BUTTON_OFFSET_X = -8;
        public static final int DEFAULT_INVENTORY_BUTTON_OFFSET_Y = 64;
        public static final int DEFAULT_CONTAINER_BUTTON_OFFSET_Y = 4;

        public boolean enabled = true;
        public int buttonSize = DEFAULT_BUTTON_SIZE;
        public int buttonGap = DEFAULT_BUTTON_GAP;
        public int buttonOffsetX = DEFAULT_BUTTON_OFFSET_X;
        public int inventoryButtonOffsetY = DEFAULT_INVENTORY_BUTTON_OFFSET_Y;
        public int containerButtonOffsetY = DEFAULT_CONTAINER_BUTTON_OFFSET_Y;

        public void resetButtonLayout() {
            buttonSize = DEFAULT_BUTTON_SIZE;
            buttonGap = DEFAULT_BUTTON_GAP;
            buttonOffsetX = DEFAULT_BUTTON_OFFSET_X;
            inventoryButtonOffsetY = DEFAULT_INVENTORY_BUTTON_OFFSET_Y;
            containerButtonOffsetY = DEFAULT_CONTAINER_BUTTON_OFFSET_Y;
        }
    }

    public static class QuickDropConfig {
        public boolean enabled = true;
        public boolean showButton = true;
        public int radius = 8;
    }

    public static class SlotLockConfig {
        public boolean enabled = true;
        public boolean protectPickup = true;
        public boolean dragBrush = true;
        public Map<Integer, SlotMarkConfig> playerSlots = new HashMap<>();

        public static class SlotMarkConfig {
            public String mode = "favorite";
            public String itemId = null;
        }

        /**
         * Items marcados como "favoritos" por su id de registro (ej. "minecraft:diamond").
         * El lock se aplica al stack que contiene ese item, no a un índice de slot, así
         * que sobrevive a cambios de inventario, reordenaciones y al menú de Creativo
         * (donde los slots no apuntan al Inventory del jugador en la mayoría de pestañas).
         */
        public Set<String> lockedItems = new HashSet<>();

        /**
         * @deprecated Se mantiene para no romper la deserialización de configs viejas.
         * Ya no se usa: el lock pasó de índice-de-slot a item-id.
         */
        @Deprecated
        public Set<Integer> lockedSlots = new HashSet<>();
    }

    public static class TraderConfig {
        public static final int DEFAULT_MARKED_BUY_BUTTON_SIZE = 14;
        public static final int DEFAULT_MARKED_BUY_BUTTON_GAP = 4;
        public static final int DEFAULT_MARKED_BUY_BUTTON_OFFSET_X = -206;
        public static final int DEFAULT_MARKED_BUY_BUTTON_OFFSET_Y = -15;

        public boolean enabled = true;
        public boolean markedBuyButtons = true;
        public int markedBuyButtonSize = DEFAULT_MARKED_BUY_BUTTON_SIZE;
        public int markedBuyButtonGap = DEFAULT_MARKED_BUY_BUTTON_GAP;
        public int markedBuyButtonOffsetX = DEFAULT_MARKED_BUY_BUTTON_OFFSET_X;
        public int markedBuyButtonOffsetY = DEFAULT_MARKED_BUY_BUTTON_OFFSET_Y;
        /** Trades guardados globalmente: aplican a cualquier aldeano con la misma oferta. */
        public Set<Integer> globalFavoriteTradeHashes = new HashSet<>();
        /** Trades guardados por aldeano: villagerKey -> hashes de ofertas locales. */
        public Map<String, Set<Integer>> localFavoriteTradeHashes = new HashMap<>();

        /**
         * @deprecated Campo antiguo de favoritos simples. Se migra a globalFavoriteTradeHashes
         * al cargar la config para mantener compatibilidad con instalaciones previas.
         */
        @Deprecated
        public Set<Integer> favoriteTradeHashes = new HashSet<>();

        public void resetButtonLayout() {
            markedBuyButtonSize = DEFAULT_MARKED_BUY_BUTTON_SIZE;
            markedBuyButtonGap = DEFAULT_MARKED_BUY_BUTTON_GAP;
            markedBuyButtonOffsetX = DEFAULT_MARKED_BUY_BUTTON_OFFSET_X;
            markedBuyButtonOffsetY = DEFAULT_MARKED_BUY_BUTTON_OFFSET_Y;
        }
    }

    public static class HarvestConfig {
        public boolean enabled = true;
        public int stoneHoeArea = 1;
        public int ironHoeArea = 2;
        public int diamondHoeArea = 3;
        public int netheriteHoeArea = 4;
    }

    public static class ResourcePackConfig {
        /** Activa el panel de perfiles y el drag-and-drop en la pantalla de resource packs. */
        public boolean enabled = true;
        /** Muestra el icono de papelera para eliminar packs instalados desde esa pantalla. */
        public boolean deleteButton = true;
    }
}
