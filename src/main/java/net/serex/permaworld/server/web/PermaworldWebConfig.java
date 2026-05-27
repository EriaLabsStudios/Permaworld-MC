package net.serex.permaworld.server.web;

import net.fabricmc.loader.api.FabricLoader;
import net.serex.permaworld.Permaworld;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

// Embedded web config stored in the normal Fabric config directory.
// enabled=false prevents the HTTP server from starting with the world/server.
// host should stay on localhost unless we add proper auth/security.
// port is the browser port to open on the same machine.
public record PermaworldWebConfig(boolean enabled, String host, int port) {

    private static final String FILE_NAME = "permaworld-web.properties";

    public static PermaworldWebConfig defaults() {
        return new PermaworldWebConfig(true, "127.0.0.1", 7821);
    }

    public static Path defaultPath() {
        return FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
    }

    public static PermaworldWebConfig load(Path path) {
        PermaworldWebConfig defaults = defaults();
        Properties properties = new Properties();

        try {
            if (Files.notExists(path)) {
                writeDefaults(path, defaults);
                return defaults;
            }

            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            }

            boolean enabled = Boolean.parseBoolean(properties.getProperty("enabled", String.valueOf(defaults.enabled())));
            String host = properties.getProperty("host", defaults.host()).trim();
            if (host.isEmpty()) {
                host = defaults.host();
            }

            int port = defaults.port();
            String portValue = properties.getProperty("port", String.valueOf(defaults.port())).trim();
            try {
                port = Integer.parseInt(portValue);
            } catch (NumberFormatException exception) {
                Permaworld.LOGGER.warn("Permaworld web config tiene un port invalido en {}: {}", path, portValue);
            }

            return new PermaworldWebConfig(enabled, host, port);
        } catch (IOException exception) {
            Permaworld.LOGGER.warn("No se pudo leer la config de Permaworld web en {}. Usando defaults.", path, exception);
            return defaults;
        }
    }

    private static void writeDefaults(Path path, PermaworldWebConfig defaults) throws IOException {
        Files.createDirectories(path.getParent());

        Properties properties = new Properties();
        properties.setProperty("enabled", String.valueOf(defaults.enabled()));
        properties.setProperty("host", defaults.host());
        properties.setProperty("port", String.valueOf(defaults.port()));

        try (OutputStream output = Files.newOutputStream(path)) {
            properties.store(output, """
                    Servidor Web Embebido de Permaworld
                    enabled=true (true para activar la web al iniciar el juego/servidor, false para desactivarla)
                    host=127.0.0.1 (Establece a 0.0.0.0 para permitir el acceso desde el exterior en tu servidor de Minecraft)
                    port=7821 (Puerto HTTP en el que se abrira la consola y estadisticas web)
                    """.stripIndent().trim());
        }
    }
}
