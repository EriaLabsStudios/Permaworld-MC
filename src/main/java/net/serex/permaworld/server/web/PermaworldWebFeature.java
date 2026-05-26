package net.serex.permaworld.server.web;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.serex.permaworld.Permaworld;

import java.nio.file.Path;

public final class PermaworldWebFeature {

    private final Path configPath = PermaworldWebConfig.defaultPath();
    private PermaworldHttpServer httpServer;

    public void register() {
        Permaworld.LOGGER.info("Permaworld web config file: {}", configPath);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            PermaworldWebConfig config = PermaworldWebConfig.load(configPath);
            Permaworld.LOGGER.info("Permaworld web config: enabled={}, host={}, port={}",
                    config.enabled(), config.host(), config.port());
            if (!config.enabled()) {
                Permaworld.LOGGER.info("Permaworld web deshabilitada por configuracion en {}. Pon enabled=true y reinicia el juego/servidor.", configPath);
                return;
            }
            Permaworld.LOGGER.info("Inicializando Permaworld web para el mundo/servidor actual...");
            httpServer = new PermaworldHttpServer(server, config);
            httpServer.start();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (httpServer != null) {
                Permaworld.LOGGER.info("Deteniendo Permaworld web...");
                httpServer.stop();
                httpServer = null;
            }
        });
    }
}
