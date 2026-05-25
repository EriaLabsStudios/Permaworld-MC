package net.serex.permaworld.client.feature.slotlock;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.serex.permaworld.client.feature.FeatureModule;

/**
 * Módulo de Slot Lock. Toda la lógica vive en {@link SlotLockManager} y
 * en {@code AbstractContainerScreenMixin}; este módulo existe para mantener
 * el patrón uniforme de registro de features y para futuras extensiones
 * (HUD, comandos, etc.).
 */
public final class SlotLockFeatureModule implements FeatureModule {

    @Override
    public void onClientInit() {
        ClientTickEvents.END_CLIENT_TICK.register(SlotPickupProtector::tick);
    }
}
