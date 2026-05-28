package net.serex.permaworld.client.feature.quickdrop;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.serex.permaworld.client.config.ConfigManager;
import net.serex.permaworld.client.debug.DebugLog;
import net.serex.permaworld.client.feature.FeatureModule;
import net.serex.permaworld.client.keybind.KeyPoller;
import net.serex.permaworld.client.keybind.Keybinds;

/**
 * Módulo de feature para el depósito rápido (Quick Drop Stack).
 */
public final class QuickDropFeatureModule implements FeatureModule {

    private KeyPoller poller;

    @Override
    public void onClientInit() {
        this.poller = new KeyPoller(Keybinds.quickDropStack);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (QuickDropHandler.isQuickDropping()) {
                QuickDropHandler.tickAutoDrop();
            }

            if (!ConfigManager.get().config().quickDrop.enabled) {
                return;
            }
            if (!poller.justPressed()) {
                return;
            }

            DebugLog.log("quickdrop", "Tecla pulsada (quick_drop_stack).");
            QuickDropHandler.triggerQuickDrop();
        });
    }
}
