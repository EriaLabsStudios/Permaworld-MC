package net.serex.permaworld.server.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebRestoreServiceTest {

    @Test
    void restoreServiceChecksPermissionsAndInventorySnapshotOnly() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/server/web/WebRestoreService.java"
        ));

        assertTrue(source.contains("isSingleplayerOwner"));
        assertTrue(source.contains("isOp"));
        assertTrue(source.contains("\"inventory_snapshot\""));
        assertTrue(source.contains("InventoryChestRestorer"));
        assertTrue(source.contains("Admin no valido o sin permisos."));
    }
}
