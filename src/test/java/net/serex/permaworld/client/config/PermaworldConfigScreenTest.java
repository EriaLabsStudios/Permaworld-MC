package net.serex.permaworld.client.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermaworldConfigScreenTest {

    @Test
    void modMenuConfigIsGroupedByFeatureTabs() throws IOException {
        String configSource = Files.readString(Path.of(
                "src/client/java/net/serex/permaworld/client/config/PermaworldConfig.java"
        ));
        String screenSource = Files.readString(Path.of(
                "src/client/java/net/serex/permaworld/client/config/PermaworldConfigScreen.java"
        ));
        String spanishLang = Files.readString(Path.of("src/client/resources/assets/permaworld/lang/es_es.json"));
        String englishLang = Files.readString(Path.of("src/client/resources/assets/permaworld/lang/en_us.json"));

        assertTrue(configSource.contains("public boolean markedBuyButtons = true;"));
        assertTrue(configSource.contains("public int markedBuyButtonOffsetX"));
        assertTrue(configSource.contains("public int markedBuyButtonOffsetY"));
        assertTrue(configSource.contains("public int markedBuyButtonSize"));
        assertTrue(configSource.contains("public int markedBuyButtonGap"));
        assertTrue(configSource.contains("resetButtonLayout()"));
        assertTrue(screenSource.contains("private enum ConfigTab"));
        assertTrue(screenSource.contains("ConfigTab.GENERAL"));
        assertTrue(screenSource.contains("SORT(\"permaworld.config.tab.sort\")"));
        assertTrue(screenSource.contains("QUICK_DROP(\"permaworld.config.tab.quick_drop\")"));
        assertTrue(screenSource.contains("SLOT_LOCK(\"permaworld.config.tab.slot_lock\")"));
        assertTrue(screenSource.contains("TRADER(\"permaworld.config.tab.trader\")"));
        assertTrue(screenSource.contains("HARVEST(\"permaworld.config.tab.harvest\")"));
        assertTrue(screenSource.contains("permaworld.config.trader.marked_buy_buttons"));
        assertTrue(screenSource.contains("config().trader.markedBuyButtons"));
        assertTrue(screenSource.contains("permaworld.config.trader.buy_button_offset_x"));
        assertTrue(screenSource.contains("permaworld.config.trader.buy_button_offset_y"));
        assertTrue(screenSource.contains("permaworld.config.trader.buy_button_size"));
        assertTrue(screenSource.contains("permaworld.config.trader.buy_button_gap"));
        assertTrue(screenSource.contains("config().trader.resetButtonLayout()"));
        assertTrue(englishLang.contains("permaworld.config.tab.general"));
        assertTrue(englishLang.contains("permaworld.config.tab.trader"));
        assertTrue(spanishLang.contains("permaworld.config.trader.marked_buy_buttons"));
        assertTrue(englishLang.contains("permaworld.config.trader.marked_buy_buttons"));
        assertTrue(spanishLang.contains("permaworld.config.trader.buy_button_offset_x"));
        assertTrue(englishLang.contains("permaworld.config.trader.buy_button_offset_x"));
    }
}
