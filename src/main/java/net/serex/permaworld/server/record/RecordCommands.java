package net.serex.permaworld.server.record;

import com.google.gson.JsonObject;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class RecordCommands {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 25;
    private static final DateTimeFormatter CHAT_TIME = DateTimeFormatter.ofPattern("dd/MM HH:mm")
            .withZone(ZoneId.systemDefault());

    public void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("permaworld")
                .then(newLogCommands())
                .then(literal("web")
                        .requires(source -> hasPermission(source, 2))
                        .then(literal("reload")
                                .executes(context -> reloadWeb(context.getSource())))
                        .then(literal("status")
                                .executes(context -> statusWeb(context.getSource())))));
        dispatcher.register(legacyLogCommands());
    }

    private LiteralArgumentBuilder<CommandSourceStack> newLogCommands() {
        return literal("log")
                .then(literal("list")
                        .executes(context -> listOwn(context.getSource(), DEFAULT_LIMIT))
                        .then(literal("limit")
                                .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                        .executes(context -> listOwn(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "limit")))))
                        .then(argument("player", EntityArgument.player())
                                .requires(source -> hasPermission(source, 2))
                                .executes(context -> listAdmin(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), DEFAULT_LIMIT))
                                .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                        .executes(context -> listAdmin(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "limit"))))))
                .then(literal("type")
                        .then(argument("reason", StringArgumentType.word())
                                .executes(context -> listOwn(context.getSource(), DEFAULT_LIMIT,
                                        StringArgumentType.getString(context, "reason")))
                                .then(literal("limit")
                                        .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                                .executes(context -> listOwn(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "limit"),
                                                        StringArgumentType.getString(context, "reason")))))
                                .then(argument("player", EntityArgument.player())
                                        .requires(source -> hasPermission(source, 2))
                                        .executes(context -> listAdmin(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"), DEFAULT_LIMIT,
                                                StringArgumentType.getString(context, "reason")))
                                        .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                                .executes(context -> listAdmin(context.getSource(),
                                                        EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "limit"),
                                                        StringArgumentType.getString(context, "reason")))))))
                .then(literal("deaths") // /permaworld log deaths <player>
                        .executes(context -> listOwn(context.getSource(), DEFAULT_LIMIT, "DEATH"))
                        .then(literal("limit")
                                .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                        .executes(context -> listOwn(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "limit"), "DEATH"))))
                        .then(argument("player", EntityArgument.player())
                                .requires(source -> hasPermission(source, 2))
                                .executes(context -> listAdmin(context.getSource(),
                                        EntityArgument.getPlayer(context, "player"), DEFAULT_LIMIT, "DEATH"))
                                .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                        .executes(context -> listAdmin(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                IntegerArgumentType.getInteger(context, "limit"), "DEATH")))))
                .then(literal("show")
                        .then(argument("recordId", StringArgumentType.word())
                                .executes(context -> showOwn(context.getSource(),
                                        StringArgumentType.getString(context, "recordId"))))
                        .then(argument("player", EntityArgument.player())
                                .requires(source -> hasPermission(source, 2))
                                .then(argument("recordId", StringArgumentType.word())
                                        .executes(context -> showAdmin(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "recordId"))))))
                .then(literal("snapshot")
                        .executes(context -> snapshot(context.getSource())))
                .then(literal("restore")
                        .requires(source -> hasPermission(source, 2))
                        .then(argument("player", EntityArgument.player())
                                .then(argument("recordId", StringArgumentType.word())
                                        .executes(context -> restore(context.getSource(),
                                                EntityArgument.getPlayer(context, "player"),
                                                StringArgumentType.getString(context, "recordId"))))));
    }

    private LiteralArgumentBuilder<CommandSourceStack> legacyLogCommands() {
        return literal("permaworldlog")
                .then(literal("list")
                        .executes(context -> listOwn(context.getSource(), DEFAULT_LIMIT))
                        .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                .executes(context -> listOwn(context.getSource(), IntegerArgumentType.getInteger(context, "limit"))))
                        .then(literal("type")
                                .then(argument("reason", StringArgumentType.word())
                                        .executes(context -> listOwn(context.getSource(), DEFAULT_LIMIT,
                                                StringArgumentType.getString(context, "reason")))
                                        .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                                .executes(context -> listOwn(context.getSource(),
                                                        IntegerArgumentType.getInteger(context, "limit"),
                                                        StringArgumentType.getString(context, "reason")))))))
                .then(literal("deaths")
                        .executes(context -> listOwn(context.getSource(), DEFAULT_LIMIT, "DEATH"))
                        .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                .executes(context -> listOwn(context.getSource(), IntegerArgumentType.getInteger(context, "limit"),
                                        "DEATH"))))
                .then(literal("show")
                        .then(argument("recordId", StringArgumentType.word())
                                .executes(context -> showOwn(context.getSource(), StringArgumentType.getString(context, "recordId")))))
                .then(literal("snapshot")
                        .executes(context -> snapshot(context.getSource())))
                .then(literal("admin")
                        .requires(source -> hasPermission(source, 2))
                        .then(literal("list")
                                .then(argument("player", EntityArgument.player())
                                        .executes(context -> listAdmin(context.getSource(), EntityArgument.getPlayer(context, "player"), DEFAULT_LIMIT))
                                        .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                                .executes(context -> listAdmin(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "limit"))))
                                        .then(literal("type")
                                                .then(argument("reason", StringArgumentType.word())
                                                        .executes(context -> listAdmin(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                                DEFAULT_LIMIT, StringArgumentType.getString(context, "reason")))
                                                        .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                                                .executes(context -> listAdmin(context.getSource(),
                                                                        EntityArgument.getPlayer(context, "player"),
                                                                        IntegerArgumentType.getInteger(context, "limit"),
                                                                        StringArgumentType.getString(context, "reason"))))))))
                        .then(literal("deaths")
                                .then(argument("player", EntityArgument.player())
                                        .executes(context -> listAdmin(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                DEFAULT_LIMIT, "DEATH"))
                                        .then(argument("limit", IntegerArgumentType.integer(1, MAX_LIMIT))
                                                .executes(context -> listAdmin(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                        IntegerArgumentType.getInteger(context, "limit"), "DEATH")))))
                        .then(literal("show")
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("recordId", StringArgumentType.word())
                                                .executes(context -> showAdmin(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "recordId"))))))
                        .then(literal("restore")
                                .then(argument("player", EntityArgument.player())
                                        .then(argument("recordId", StringArgumentType.word())
                                                .executes(context -> restore(context.getSource(), EntityArgument.getPlayer(context, "player"),
                                                        StringArgumentType.getString(context, "recordId")))))));
    }

    private int listOwn(CommandSourceStack source, int limit) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return list(source, source.getPlayerOrException(), limit, null);
    }

    private int listOwn(CommandSourceStack source, int limit, String reasonFilter)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return list(source, source.getPlayerOrException(), limit, reasonFilter);
    }

    private int listAdmin(CommandSourceStack source, ServerPlayer player, int limit) {
        return list(source, player, limit, null);
    }

    private int listAdmin(CommandSourceStack source, ServerPlayer player, int limit, String reasonFilter) {
        return list(source, player, limit, reasonFilter);
    }

    private int list(CommandSourceStack source, ServerPlayer player, int limit, String reasonFilter) {
        try {
            List<RecordSummary> records = PermaworldRecordStore.forServer(source.getServer())
                    .latestPlayerRecords(player.getUUID(), Math.min(limit, MAX_LIMIT), reasonFilter);
            if (records.isEmpty()) {
                source.sendFailure(Component.literal(noRecordsMessage(player, reasonFilter)));
                return 0;
            }
            source.sendSuccess(() -> Component.literal(recordsHeader(player, reasonFilter))
                    .withStyle(ChatFormatting.GRAY), false);
            for (int index = 0; index < records.size(); index++) {
                int rowIndex = index + 1;
                RecordSummary record = records.get(index);
                boolean canRestore = hasPermission(source, 2) && isRestorable(record);
                source.sendSuccess(() -> formatRecordRow(rowIndex, player, record, canRestore), false);
            }
            return records.size();
        } catch (IOException e) {
            source.sendFailure(Component.literal("No se pudieron leer los registros: " + e.getMessage()));
            return 0;
        }
    }

    private int showOwn(CommandSourceStack source, String recordId) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        return show(source, source.getPlayerOrException(), recordId);
    }

    private int showAdmin(CommandSourceStack source, ServerPlayer player, String recordId) {
        return show(source, player, recordId);
    }

    private int show(CommandSourceStack source, ServerPlayer player, String recordId) {
        try {
            JsonObject record = PermaworldRecordStore.forServer(source.getServer())
                    .findPlayerRecord(player.getUUID(), recordId)
                    .orElse(null);
            if (record == null) {
                source.sendFailure(Component.literal("Registro no encontrado: " + recordId));
                return 0;
            }
            RecordSummary summary = RecordSummary.fromJson(record);
            boolean canRestore = hasPermission(source, 2) && isRestorable(summary);
            source.sendSuccess(() -> formatRecordRow(1, player, summary, canRestore), false);
            return 1;
        } catch (IOException e) {
            source.sendFailure(Component.literal("No se pudo leer el registro: " + e.getMessage()));
            return 0;
        }
    }

    private int snapshot(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        InventorySnapshotService.appendSnapshot(source.getServer(), player, "MANUAL_SNAPSHOT");
        source.sendSuccess(() -> Component.literal("Snapshot registrado."), false);
        return 1;
    }

    private int restore(CommandSourceStack source, ServerPlayer target, String recordId)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer admin = source.getPlayerOrException();
        try {
            JsonObject record = PermaworldRecordStore.forServer(source.getServer())
                    .findPlayerRecord(target.getUUID(), recordId)
                    .orElse(null);
            if (record == null) {
                source.sendFailure(Component.literal("Registro no encontrado: " + recordId));
                return 0;
            }
            int restored = new InventoryChestRestorer().restore(admin, record);
            source.sendSuccess(() -> Component.literal("Restaurados " + restored + " stacks en cofre(s)."), true);
            return restored;
        } catch (IOException | RuntimeException e) {
            source.sendFailure(Component.literal("No se pudo restaurar el registro: " + e.getMessage()));
            return 0;
        }
    }

    private static boolean hasPermission(CommandSourceStack source, int level) {
        try {
            NameAndId nameAndId = new NameAndId(source.getPlayerOrException().getGameProfile());
            return source.getServer().isSingleplayerOwner(nameAndId)
                    || source.getServer().getPlayerList().isOp(nameAndId);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
            return level <= 2;
        }
    }

    private static Component formatRecordRow(int index, ServerPlayer player, RecordSummary record, boolean canRestore) {
        MutableComponent row = Component.literal(index + ". ")
                .withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.literal(record.reason()).withStyle(ChatFormatting.YELLOW))
                .append(Component.literal(" - " + formatTimestamp(record.timestamp())).withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" - " + record.itemCount() + " items").withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" #" + record.id().substring(0, 8)).withStyle(ChatFormatting.DARK_GRAY));
        if (canRestore) {
            row.append(Component.literal(" "));
            row.append(restoreAction(player, record));
        }
        return row;
    }

    private static Component restoreAction(ServerPlayer player, RecordSummary record) {
        String command = "/permaworld log restore " + player.getName().getString() + " " + record.id();
        return Component.literal("[Restaurar]")
                .withStyle(style -> style.withColor(ChatFormatting.BLUE)
                        .withClickEvent(new ClickEvent.RunCommand(command)));
    }

    private static boolean isRestorable(RecordSummary record) {
        return "inventory_snapshot".equals(record.type());
    }

    private static String recordsHeader(ServerPlayer player, String reasonFilter) {
        if (reasonFilter == null || reasonFilter.isBlank()) {
            return "Registros de " + player.getName().getString() + ":";
        }
        return "Registros de " + player.getName().getString() + " filtrados por "
                + PermaworldRecordStore.normalizeReasonFilter(reasonFilter) + ":";
    }

    private static String noRecordsMessage(ServerPlayer player, String reasonFilter) {
        if (reasonFilter == null || reasonFilter.isBlank()) {
            return "No hay registros para " + player.getName().getString();
        }
        return "No hay registros " + PermaworldRecordStore.normalizeReasonFilter(reasonFilter)
                + " para " + player.getName().getString();
    }

    private static String formatTimestamp(String timestamp) {
        try {
            return CHAT_TIME.format(Instant.parse(timestamp));
        } catch (RuntimeException ignored) {
            return timestamp;
        }
    }

    private int reloadWeb(CommandSourceStack source) {
        net.serex.permaworld.server.web.PermaworldWebFeature feature = net.serex.permaworld.server.web.PermaworldWebFeature.getInstance();
        if (feature == null) {
            source.sendFailure(Component.literal("Caracteristica web no registrada."));
            return 0;
        }
        boolean active = feature.reload(source.getServer());
        if (active) {
            source.sendSuccess(() -> Component.literal("Servidor web de Permaworld recargado y " + feature.getStatusString())
                    .withStyle(net.minecraft.ChatFormatting.GREEN), true);
        } else {
            source.sendSuccess(() -> Component.literal("Servidor web de Permaworld recargado y detenido (deshabilitado o fallido en la carga de config).")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
        }
        return 1;
    }

    private int statusWeb(CommandSourceStack source) {
        net.serex.permaworld.server.web.PermaworldWebFeature feature = net.serex.permaworld.server.web.PermaworldWebFeature.getInstance();
        if (feature == null) {
            source.sendFailure(Component.literal("Caracteristica web no registrada."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Servidor web de Permaworld " + feature.getStatusString())
                .withStyle(net.minecraft.ChatFormatting.GRAY), false);
        return 1;
    }
}
