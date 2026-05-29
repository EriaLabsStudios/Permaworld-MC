package net.serex.permaworld.server.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAssetsSignatureTest {

    @Test
    void webShellAndAssetsExistWithMinecraftForwardStyling() throws IOException {
        String html = Files.readString(Path.of("src/main/resources/assets/permaworld/web/index.html"));
        String css = Files.readString(Path.of("src/main/resources/assets/permaworld/web/app.css"));
        String js = Files.readString(Path.of("src/main/resources/assets/permaworld/web/app.js"));

        assertTrue(html.contains("Permaworld Logbook"));
        assertTrue(html.contains("filterBar"));
        assertTrue(html.contains("STATS"));         // filter button for Statistics tab
        assertTrue(css.contains("Outfit"));          // primary UI font (replaced Monocraft)
        assertTrue(css.contains("--grass-1"));
        assertTrue(css.contains(".stat-grid"));
        assertTrue(css.contains(".advancement-grid"));
        assertTrue(css.contains(".mc-button"));
        assertTrue(js.contains("/api/players"));
        assertTrue(js.contains("/api/item-texture"));
        assertTrue(js.contains("/stats"));
        assertTrue(js.contains("Create recovery chest"));
        assertTrue(js.contains("Select an advancement to inspect it here."));
    }
}
