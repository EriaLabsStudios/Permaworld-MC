package net.serex.permaworld.server.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermaworldWebFeatureSignatureTest {

    @Test
    void mainEntrypointRegistersPermaworldWebFeature() throws IOException {
        String source = Files.readString(Path.of("src/main/java/net/serex/permaworld/Permaworld.java"));

        assertTrue(source.contains("PermaworldWebFeature"));
        assertTrue(source.contains("new PermaworldWebFeature().register()"));
    }

    @Test
    void webFeatureExposesRegisterEntrypoint() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/server/web/PermaworldWebFeature.java"
        ));

        assertTrue(source.contains("public final class PermaworldWebFeature"));
        assertTrue(source.contains("public void register()"));
    }
}
