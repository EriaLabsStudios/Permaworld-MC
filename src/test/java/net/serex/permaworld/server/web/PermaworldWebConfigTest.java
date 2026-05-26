package net.serex.permaworld.server.web;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermaworldWebConfigTest {

    @Test
    void defaultsUseExpectedLocalhostAndPort() {
        PermaworldWebConfig config = PermaworldWebConfig.defaults();

        assertNotNull(config);
        assertEquals("127.0.0.1", config.host());
        assertEquals(7821, config.port());
    }

    @Test
    void loadCreatesDocumentedConfigFileWhenMissing(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("permaworld-web.properties");

        PermaworldWebConfig config = PermaworldWebConfig.load(configFile);

        assertTrue(Files.exists(configFile));
        assertEquals(PermaworldWebConfig.defaults(), config);
        String text = Files.readString(configFile);
        assertTrue(text.contains("enabled"));
        assertTrue(text.contains("host"));
        assertTrue(text.contains("port"));
    }

    @Test
    void loadReadsEnabledOverrideFromPropertiesFile(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("permaworld-web.properties");
        Files.writeString(configFile, """
                enabled=false
                host=127.0.0.1
                port=7821
                """);

        PermaworldWebConfig config = PermaworldWebConfig.load(configFile);

        assertFalse(config.enabled());
        assertEquals("127.0.0.1", config.host());
        assertEquals(7821, config.port());
    }
}
