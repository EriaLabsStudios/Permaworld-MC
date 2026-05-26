package net.serex.permaworld.server.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermaworldHttpRoutesTest {

    @Test
    void serverDeclaresPlayerAndRecordApiRoutes() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/server/web/PermaworldHttpServer.java"
        ));

        assertTrue(source.contains("\"/api/session\""));
        assertTrue(source.contains("\"/api/players\""));
        assertTrue(source.contains("\"/api/item-texture\""));
        assertTrue(source.contains("\"records\""));
        assertTrue(source.contains("\"stats\""));
        assertTrue(source.contains("\"restore\""));
        assertTrue(source.contains("WebRecordQueryService"));
        assertTrue(source.contains("WebRestoreService"));
    }
}
