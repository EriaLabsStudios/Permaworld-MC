package net.serex.permaworld.mixin.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PackSelectionScreenMixinSignatureTest {

    @Test
    void mouseClickedIsImplementedInsteadOfInjected() throws IOException {
        String source = Files.readString(Path.of(
                "src/client/java/net/serex/permaworld/mixin/client/PackSelectionScreenMixin.java"
        ));

        assertFalse(source.contains("@Inject(method = \"mouseClicked\""));
        assertTrue(source.contains("public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick)"));
    }
}
