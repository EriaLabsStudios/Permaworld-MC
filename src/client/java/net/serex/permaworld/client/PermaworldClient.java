package net.serex.permaworld.client;

import net.fabricmc.api.ClientModInitializer;
import net.serex.permaworld.Permaworld;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.FeatureModule;
import net.serex.permaworld.client.feature.slotlock.SlotLockFeatureModule;
import net.serex.permaworld.client.feature.sort.SortFeatureModule;
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

        Permaworld.LOGGER.info("Permaworld client inicializado con {} módulo(s).", MODULES.size());
    }

    private static void registerModules() {
        MODULES.add(new SortFeatureModule());
        MODULES.add(new SlotLockFeatureModule());
        // Las features se irán enganchando aquí en milestones siguientes.
    }
}
