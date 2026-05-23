package net.serex.permaworld.client.feature.sort;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.feature.FeatureModule;
import net.serex.permaworld.client.keybind.Keybinds;

/**
 * Engancha la feature de ordenar inventario al tick del cliente.
 * Cada vez que el jugador pulsa el keybind configurado y hay un menú abierto
 * con su inventario, se ejecuta {@link InventorySorter#sortPlayerInventory()}.
 */
public final class SortFeatureModule implements FeatureModule {

    @Override
    public void onClientInit() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!ConfigManager.get().config().sort.enabled) {
                return;
            }
            // consumeClick devuelve true por cada pulsación pendiente; así
            // procesamos cada pulsación una sola vez aunque se mantenga apretada.
            while (Keybinds.sortInventory.consumeClick()) {
                if (client.player == null || client.player.containerMenu == null) {
                    continue;
                }
                InventorySorter.sortPlayerInventory();
            }
        });
    }
}
