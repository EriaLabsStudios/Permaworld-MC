package net.serex.permaworld;

import net.fabricmc.api.ModInitializer;
import net.serex.permaworld.server.record.ServerRecordFeature;
import net.serex.permaworld.server.web.PermaworldWebFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Permaworld implements ModInitializer {

    public static final String MOD_ID = "permaworld";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        new ServerRecordFeature().register();
        new PermaworldWebFeature().register();
    }
}
