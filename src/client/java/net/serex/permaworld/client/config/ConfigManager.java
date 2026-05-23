package net.serex.permaworld.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import net.serex.permaworld.Permaworld;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Carga y persiste {@link PermaworldConfig} usando Gson.
 * Archivo: {@code <gameDir>/config/permaworld.json}.
 */
public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "permaworld.json";

    private static ConfigManager instance;

    private final Path configPath;
    private PermaworldConfig config;

    private ConfigManager(Path configPath) {
        this.configPath = configPath;
    }

    public static ConfigManager get() {
        if (instance == null) {
            Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
            instance = new ConfigManager(path);
            instance.load();
        }
        return instance;
    }

    public PermaworldConfig config() {
        return config;
    }

    public void load() {
        try {
            if (Files.exists(configPath)) {
                try (var reader = Files.newBufferedReader(configPath)) {
                    config = GSON.fromJson(reader, PermaworldConfig.class);
                }
            }
            if (config == null) {
                config = new PermaworldConfig();
                save();
            }
        } catch (IOException e) {
            Permaworld.LOGGER.error("No se pudo cargar permaworld.json, usando defaults", e);
            config = new PermaworldConfig();
        }
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            try (var writer = Files.newBufferedWriter(configPath)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            Permaworld.LOGGER.error("No se pudo guardar permaworld.json", e);
        }
    }
}
