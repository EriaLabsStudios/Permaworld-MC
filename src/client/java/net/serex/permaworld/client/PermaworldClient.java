package net.serex.permaworld.client;

import net.fabricmc.api.ClientModInitializer;
import net.serex.permaworld.Permaworld;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.FeatureModule;
import net.serex.permaworld.client.feature.harvest.RightClickHarvest;
import net.serex.permaworld.client.feature.slotlock.SlotLockFeatureModule;
import net.serex.permaworld.client.feature.sort.SortFeatureModule;
import net.serex.permaworld.client.feature.trader.TraderQuickBuyFeatureModule;
import net.serex.permaworld.client.feature.quickdrop.QuickDropFeatureModule;
import net.serex.permaworld.client.keybind.Keybinds;

import java.util.ArrayList;
import java.util.List;

public class PermaworldClient implements ClientModInitializer {

    private static final List<FeatureModule> MODULES = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        // Carga la config antes de que las features la consulten.
        ConfigManager.get();

        // Registra los keybinds globales del mod.
        Keybinds.register();

        // Registra cada FeatureModule. A medida que se vayan añadiendo features
        // en próximas milestones, se añaden aquí.
        registerModules();

        for (FeatureModule module : MODULES) {
            module.onClientInit();
        }

        boolean debug = ConfigManager.get().config().debug;
        Permaworld.LOGGER.info("Permaworld client inicializado con {} módulo(s). debug={}",
                MODULES.size(), debug);
        if (debug) {
            Permaworld.LOGGER.info("[Permaworld][debug] Modo debug ACTIVO. Las features loguearan con prefijo [Permaworld][debug][<feature>].");
            Permaworld.LOGGER.info("[Permaworld][debug] Keybinds registrados: sort='{}', quickDrop='{}', slotLockModifier='{}'.",
                    Keybinds.sortInventory.saveString(),
                    Keybinds.quickDropStack.saveString(),
                    Keybinds.slotLockModifier.saveString());
        }
    }

    private static void registerModules() {
        MODULES.add(new SortFeatureModule());
        MODULES.add(new SlotLockFeatureModule());
        MODULES.add(new RightClickHarvest());
        MODULES.add(new TraderQuickBuyFeatureModule());
        MODULES.add(new QuickDropFeatureModule());
    }
}
