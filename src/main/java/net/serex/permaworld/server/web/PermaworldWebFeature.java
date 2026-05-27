package net.serex.permaworld.server.web;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.serex.permaworld.Permaworld;

import java.nio.file.Path;

public final class PermaworldWebFeature {

    private static PermaworldWebFeature instance;

    public static PermaworldWebFeature getInstance() {
        return instance;
    }

    private final Path configPath = PermaworldWebConfig.defaultPath();
    private PermaworldHttpServer httpServer;

    public void register() {
        instance = this;
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

    public boolean reload(MinecraftServer server) {
        try {
            if (httpServer != null) {
                Permaworld.LOGGER.info("Deteniendo Permaworld web para recarga...");
                httpServer.stop();
                httpServer = null;
            }
            PermaworldWebConfig config = PermaworldWebConfig.load(configPath);
            Permaworld.LOGGER.info("Permaworld web config recargada: enabled={}, host={}, port={}",
                    config.enabled(), config.host(), config.port());
            if (!config.enabled()) {
                Permaworld.LOGGER.info("Permaworld web deshabilitada por configuracion recargada.");
                return false;
            }
            httpServer = new PermaworldHttpServer(server, config);
            httpServer.start();
            return true;
        } catch (Exception e) {
            Permaworld.LOGGER.error("Error al recargar el servidor web de Permaworld", e);
            return false;
        }
    }

    public String getStatusString() {
        if (httpServer == null) {
            return "detenido";
        }
        PermaworldWebConfig config = PermaworldWebConfig.load(configPath);
        return String.format("ejecutandose en http://%s:%d/", config.host(), config.port());
    }
}
