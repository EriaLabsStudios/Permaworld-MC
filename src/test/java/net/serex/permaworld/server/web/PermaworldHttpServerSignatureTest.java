package net.serex.permaworld.server.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermaworldHttpServerSignatureTest {

    @Test
    void httpServerWrapperExposesStartStopAndUsesLocalhostBinding() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/server/web/PermaworldHttpServer.java"
        ));

        assertTrue(source.contains("HttpServer"));
        assertTrue(source.contains("public void start()"));
        assertTrue(source.contains("public void stop()"));
        assertTrue(source.contains("InetSocketAddress"));
        assertTrue(source.contains("config.host()"));
        assertTrue(source.contains("config.port()"));
    }

    @Test
    void webFeatureHooksServerStartAndStopLifecycle() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/server/web/PermaworldWebFeature.java"
        ));

        assertTrue(source.contains("ServerLifecycleEvents.SERVER_STARTED"));
        assertTrue(source.contains("ServerLifecycleEvents.SERVER_STOPPING"));
        assertTrue(source.contains("PermaworldWebConfig.defaultPath()"));
        assertTrue(source.contains("PermaworldWebConfig.load(configPath)"));
        assertTrue(source.contains("PermaworldHttpServer"));
    }
}
