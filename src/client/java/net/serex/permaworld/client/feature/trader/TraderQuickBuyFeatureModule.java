package net.serex.permaworld.client.feature.trader;

import net.serex.permaworld.client.feature.FeatureModule;

public final class TraderQuickBuyFeatureModule implements FeatureModule {

    @Override
    public void onClientInit() {
        TraderVillagerTracker.register();
    }
}
