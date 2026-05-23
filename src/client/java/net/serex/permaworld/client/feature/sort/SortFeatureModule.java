package net.serex.permaworld.client.feature.sort;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.debug.DebugLog;
import net.serex.permaworld.client.feature.FeatureModule;
import net.serex.permaworld.client.keybind.KeyPoller;
import net.serex.permaworld.client.keybind.Keybinds;

/**
 * Engancha la feature de ordenar inventario al tick del cliente.
 * <p>
 * Usa {@link KeyPoller} en vez de {@code KeyMapping.consumeClick()} porque este
 * último no se dispara mientras hay una {@code Screen} abierta, y el caso de
 * uso típico es ordenar con el inventario abierto.
 */
public final class SortFeatureModule implements FeatureModule {

    private KeyPoller poller;

    @Override
    public void onClientInit() {
        this.poller = new KeyPoller(Keybinds.sortInventory);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (!ConfigManager.get().config().sort.enabled) {
                return;
            }
            if (!poller.justPressed()) {
                return;
            }
            DebugLog.log("sort", "Tecla pulsada (sort_inventory).");
            if (client.player == null || client.player.containerMenu == null) {
                DebugLog.log("sort", "Sin jugador o sin containerMenu; se ignora.");
                return;
            }
            DebugLog.log("sort", "Ejecutando sort sobre containerMenu id={}.",
                    client.player.containerMenu.containerId);
            InventorySorter.sortPlayerInventory();
        });
    }
}
