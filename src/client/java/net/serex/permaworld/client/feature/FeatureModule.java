package net.serex.permaworld.client.feature;

/**
 * Contrato común para todas las features del cliente.
 * Cada módulo se registra en {@link net.serex.permaworld.client.PermaworldClient}
 * y aquí engancha sus eventos, keybinds y mixins.
 */
public interface FeatureModule {

    /** Se invoca una vez durante {@code onInitializeClient}. */
    void onClientInit();
}
