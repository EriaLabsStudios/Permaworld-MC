package net.serex.permaworld.server.record;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerRecordFeatureSignatureTest {

    @Test
    void mainEntrypointRegistersServerRecordFeature() throws IOException {
        String source = Files.readString(Path.of("src/main/java/net/serex/permaworld/Permaworld.java"));

        assertTrue(source.contains("ServerRecordFeature"));
        assertTrue(source.contains("new ServerRecordFeature().register()"));
    }

    @Test
    void serverFeatureHooksExpectedFabricEvents() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/server/record/ServerRecordFeature.java"
        ));
        String mixins = Files.readString(Path.of("src/main/resources/permaworld.mixins.json"));
        String advancementMixin = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/mixin/PlayerAdvancementsMixin.java"
        ));
        String deathMixin = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/mixin/ServerPlayerDeathMixin.java"
        ));

        assertTrue(source.contains("ServerPlayConnectionEvents.JOIN"));
        assertTrue(source.contains("ServerPlayConnectionEvents.DISCONNECT"));
        assertFalse(source.contains("ServerLivingEntityEvents.AFTER_DEATH"));
        assertTrue(source.contains("ServerLifecycleEvents.SERVER_STARTED"));
        assertTrue(source.contains("ServerLifecycleEvents.SERVER_STOPPING"));
        assertTrue(source.contains("ServerTickEvents.END_SERVER_TICK"));
        assertTrue(source.contains("CommandRegistrationCallback.EVENT"));
        assertTrue(mixins.contains("PlayerAdvancementsMixin"));
        assertTrue(mixins.contains("ServerPlayerDeathMixin"));
        assertTrue(advancementMixin.contains("ADVANCEMENT_DONE"));
        assertTrue(advancementMixin.contains("method = \"award\""));
        assertTrue(deathMixin.contains("method = \"die\""));
        assertTrue(deathMixin.contains("@At(\"HEAD\")"));
        assertTrue(deathMixin.contains("\"DEATH\""));
    }

    @Test
    void commandsExposePlayerAndAdminRecordFlows() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/server/record/RecordCommands.java"
        ));

        assertTrue(source.contains("literal(\"permaworldlog\")"));
        assertTrue(source.contains("literal(\"permaworld\")"));
        assertTrue(source.contains("literal(\"log\")"));
        assertTrue(source.contains("literal(\"list\")"));
        assertTrue(source.contains("literal(\"show\")"));
        assertTrue(source.contains("literal(\"snapshot\")"));
        assertTrue(source.contains("literal(\"deaths\")"));
        assertTrue(source.contains("literal(\"type\")"));
        assertTrue(source.contains("literal(\"admin\")"));
        assertTrue(source.contains("hasPermission(source, 2)"));
        assertTrue(source.contains("EntityArgument.player()"));
        assertTrue(source.contains("InventoryChestRestorer"));
        assertTrue(source.contains("newLogCommands()"));
        assertTrue(source.contains("legacyLogCommands()"));
        assertTrue(source.contains("deaths <player>"));
    }

    @Test
    void commandsRenderCompactRowsAndAdminRestoreAction() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/server/record/RecordCommands.java"
        ));

        assertTrue(source.contains("formatRecordRow"));
        assertTrue(source.contains("formatTimestamp"));
        assertTrue(source.contains("restoreAction"));
        assertTrue(source.contains("ClickEvent.RunCommand"));
        assertTrue(source.contains("ChatFormatting.BLUE"));
        assertTrue(source.contains("\"[Restaurar]\""));
        assertTrue(source.contains("\"/permaworld log restore \""));
        assertTrue(source.contains("record.id().substring(0, 8)"));
        assertTrue(source.contains("isRestorable(record)"));
        assertTrue(source.contains("\"inventory_snapshot\""));
        assertTrue(source.contains("normalizeReasonFilter"));
    }

    @Test
    void itemCodecUsesVanillaStackSaveAndParseWithReadableFields() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/server/record/ItemStackRecordCodec.java"
        ));

        assertTrue(source.contains("ItemStack.CODEC.encodeStart"));
        assertTrue(source.contains("ItemStack.CODEC.parse"));
        assertTrue(source.contains("DataComponents.CUSTOM_NAME"));
        assertTrue(source.contains("itemId"));
        assertTrue(source.contains("count"));
        assertTrue(source.contains("customName"));
    }

    @Test
    void pathSamplerComparesAgainstLastRecordedPathSample() throws IOException {
        String source = Files.readString(Path.of(
                "src/main/java/net/serex/permaworld/server/record/PlayerPathSampler.java"
        ));

        assertTrue(source.contains("lastPathSamples"));
        assertTrue(source.contains("previousSample.distanceToSqr(position)"));
    }
}
