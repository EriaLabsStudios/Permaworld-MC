package net.serex.permaworld.server.web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.serex.permaworld.server.record.PermaworldRecordStore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class WebRecordQueryService {

    private static final int MAX_RECORDS = 100;

    private final MinecraftServer server;
    private final PermaworldRecordStore store;

    public WebRecordQueryService(MinecraftServer server) {
        this(server, PermaworldRecordStore.forServer(server));
    }

    WebRecordQueryService(MinecraftServer server, PermaworldRecordStore store) {
        this.server = server;
        this.store = store;
    }

    public JsonObject sessionData() {
        List<String> admins = server.getPlayerList().getPlayers().stream()
                .filter(player -> isAdmin(player, server))
                .map(player -> player.getName().getString())
                .sorted()
                .toList();
        return WebDtos.session(admins);
    }

    public JsonArray playerSummaries() throws IOException {
        List<JsonObject> summaries = new ArrayList<>();
        for (UUID playerId : store.knownPlayerIds()) {
            List<JsonObject> records = store.readPlayerRecords(playerId);
            if (records.isEmpty()) {
                continue;
            }
            JsonObject latest = records.getLast();
            summaries.add(WebDtos.playerSummary(
                    playerId.toString(),
                    latest.has("playerName") ? latest.get("playerName").getAsString() : playerId.toString(),
                    latest.has("reason") ? latest.get("reason").getAsString() : "",
                    latest.has("timestamp") ? latest.get("timestamp").getAsString() : "",
                    records.size()
            ));
        }
        summaries.sort(Comparator.comparing((JsonObject json) -> json.get("lastTimestamp").getAsString()).reversed());
        JsonArray result = new JsonArray();
        summaries.forEach(result::add);
        return result;
    }

    public JsonArray playerRecords(UUID playerId, String reasonFilter) throws IOException {
        List<JsonObject> records = store.readPlayerRecords(playerId);
        records.sort(Comparator.comparing((JsonObject json) -> json.get("timestamp").getAsString()).reversed());
        JsonArray result = new JsonArray();
        String normalized = reasonFilter == null || reasonFilter.isBlank() ? "DEATH" : WebDtos.normalize(reasonFilter);
        for (JsonObject record : records) {
            String reason = record.has("reason") ? record.get("reason").getAsString() : "";
            if (!"ALL".equalsIgnoreCase(normalized) && !WebDtos.normalize(reason).equals(normalized)) {
                continue;
            }
            JsonObject dto = WebDtos.recordCard(record);
            enrichRecord(dto, record);
            result.add(dto);
            if (result.size() >= MAX_RECORDS) {
                break;
            }
        }
        return result;
    }

    public Optional<JsonObject> playerRecord(UUID playerId, String recordId) throws IOException {
        return store.findPlayerRecord(playerId, recordId).map(record -> {
            JsonObject dto = WebDtos.recordDetail(record);
            enrichRecord(dto, record);
            return dto;
        });
    }

    public JsonObject playerStats(UUID playerId) {
        JsonObject payload = new JsonObject();
        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            payload.addProperty("available", false);
            payload.addProperty("message", "Statistics are available when the player is online.");
            return payload;
        }

        ServerStatsCounter stats = server.getPlayerList().getPlayerStats(player);
        payload.addProperty("available", true);
        payload.addProperty("playerName", player.getName().getString());

        JsonArray highlights = new JsonArray();
        highlights.add(WebDtos.stat("deaths", "Deaths", stats.getValue(Stats.CUSTOM, Stats.DEATHS), Integer.toString(stats.getValue(Stats.CUSTOM, Stats.DEATHS))));
        highlights.add(WebDtos.stat("play_time", "Play Time", stats.getValue(Stats.CUSTOM, Stats.PLAY_TIME), formatTicks(stats.getValue(Stats.CUSTOM, Stats.PLAY_TIME))));
        highlights.add(WebDtos.stat("distance", "Distance", totalDistance(stats), formatDistance(totalDistance(stats))));
        highlights.add(WebDtos.stat("mob_kills", "Mob Kills", stats.getValue(Stats.CUSTOM, Stats.MOB_KILLS), Integer.toString(stats.getValue(Stats.CUSTOM, Stats.MOB_KILLS))));
        payload.add("highlights", highlights);

        payload.add("blocksMined", topBlocks(stats));
        payload.add("itemsCrafted", topItems(stats, Stats.ITEM_CRAFTED));
        payload.add("itemsPickedUp", topItems(stats, Stats.ITEM_PICKED_UP));
        payload.add("entitiesKilled", topEntities(stats));
        return payload;
    }

    public Optional<ServerPlayer> adminByName(String adminName) {
        if (adminName == null || adminName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(server.getPlayerList().getPlayerByName(adminName));
    }

    private static boolean isAdmin(ServerPlayer player, MinecraftServer server) {
        NameAndId nameAndId = new NameAndId(player.getGameProfile());
        return server.isSingleplayerOwner(nameAndId) || server.getPlayerList().isOp(nameAndId);
    }

    private void enrichRecord(JsonObject dto, JsonObject record) {
        if (!"ADVANCEMENT_DONE".equals(WebDtos.normalize(record.has("reason") ? record.get("reason").getAsString() : ""))) {
            return;
        }
        if (!record.has("metadata") || !record.get("metadata").isJsonObject() || server == null) {
            return;
        }
        JsonObject metadata = record.getAsJsonObject("metadata");
        String advancementId = metadata.has("advancement") ? metadata.get("advancement").getAsString() : "";
        if (advancementId.isBlank()) {
            return;
        }
        AdvancementHolder advancement = server.getAdvancements().get(Identifier.parse(advancementId));
        if (advancement == null) {
            return;
        }
        dto.addProperty("advancementId", advancement.id().toString());
        advancement.value().display().ifPresent(display -> fillAdvancementDisplay(dto, display));
        if (metadata.has("criterion")) {
            dto.addProperty("criterion", metadata.get("criterion").getAsString());
        }
    }

    private void fillAdvancementDisplay(JsonObject dto, DisplayInfo display) {
        ItemStack icon = display.getIcon().create();
        dto.addProperty("advancementTitle", display.getTitle().getString());
        dto.addProperty("advancementDescription", display.getDescription().getString());
        dto.addProperty("advancementFrame", display.getType().getDisplayName().getString());
        dto.addProperty("advancementIconItemId", BuiltInRegistries.ITEM.getKey(icon.getItem()).toString());
        dto.addProperty("advancementIconLabel", icon.getHoverName().getString());
        dto.addProperty("summary", display.getTitle().getString());
        dto.addProperty("reason", display.getTitle().getString());
    }

    private JsonArray topBlocks(ServerStatsCounter stats) {
        List<JsonObject> top = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            int value = stats.getValue(Stats.BLOCK_MINED, block);
            if (value > 0) {
                top.add(WebDtos.leaderboardEntry(BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName().getString(), value));
            }
        }
        top.sort(Comparator.comparingInt((JsonObject json) -> json.get("value").getAsInt()).reversed());
        return toArray(top, 8);
    }

    private JsonArray topItems(ServerStatsCounter stats, net.minecraft.stats.StatType<Item> type) {
        List<JsonObject> top = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            int value = stats.getValue(type, item);
            if (value > 0) {
                top.add(WebDtos.leaderboardEntry(BuiltInRegistries.ITEM.getKey(item).toString(), new ItemStack(item).getHoverName().getString(), value));
            }
        }
        top.sort(Comparator.comparingInt((JsonObject json) -> json.get("value").getAsInt()).reversed());
        return toArray(top, 8);
    }

    private JsonArray topEntities(ServerStatsCounter stats) {
        List<JsonObject> top = new ArrayList<>();
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            int value = stats.getValue(Stats.ENTITY_KILLED, entityType);
            if (value > 0) {
                top.add(WebDtos.leaderboardEntry(BuiltInRegistries.ENTITY_TYPE.getKey(entityType).toString(), entityType.getDescription().getString(), value));
            }
        }
        top.sort(Comparator.comparingInt((JsonObject json) -> json.get("value").getAsInt()).reversed());
        return toArray(top, 8);
    }

    private JsonArray toArray(List<JsonObject> entries, int limit) {
        JsonArray result = new JsonArray();
        entries.stream().limit(limit).forEach(result::add);
        return result;
    }

    private int totalDistance(ServerStatsCounter stats) {
        return stats.getValue(Stats.CUSTOM, Stats.WALK_ONE_CM)
                + stats.getValue(Stats.CUSTOM, Stats.SPRINT_ONE_CM)
                + stats.getValue(Stats.CUSTOM, Stats.SWIM_ONE_CM)
                + stats.getValue(Stats.CUSTOM, Stats.AVIATE_ONE_CM)
                + stats.getValue(Stats.CUSTOM, Stats.BOAT_ONE_CM)
                + stats.getValue(Stats.CUSTOM, Stats.MINECART_ONE_CM)
                + stats.getValue(Stats.CUSTOM, Stats.HORSE_ONE_CM)
                + stats.getValue(Stats.CUSTOM, Stats.WALK_UNDER_WATER_ONE_CM)
                + stats.getValue(Stats.CUSTOM, Stats.WALK_ON_WATER_ONE_CM)
                + stats.getValue(Stats.CUSTOM, Stats.FLY_ONE_CM)
                + stats.getValue(Stats.CUSTOM, Stats.CLIMB_ONE_CM);
    }

    private String formatTicks(int ticks) {
        int seconds = ticks / 20;
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        return hours + "h " + minutes + "m";
    }

    private String formatDistance(int centimeters) {
        double meters = centimeters / 100.0;
        if (meters >= 1000) {
            return String.format(java.util.Locale.ROOT, "%.2f km", meters / 1000.0);
        }
        return String.format(java.util.Locale.ROOT, "%.0f m", meters);
    }

    public JsonArray allAdvancements() {
        JsonArray array = new JsonArray();
        if (server == null) {
            return array;
        }
        for (AdvancementHolder adv : server.getAdvancements().getAllAdvancements()) {
            adv.value().display().ifPresent(display -> {
                JsonObject dto = new JsonObject();
                dto.addProperty("id", adv.id().toString());
                dto.addProperty("title", display.getTitle().getString());
                dto.addProperty("description", display.getDescription().getString());
                dto.addProperty("frame", display.getType().getDisplayName().getString());
                ItemStack icon = display.getIcon().create();
                dto.addProperty("iconItemId", BuiltInRegistries.ITEM.getKey(icon.getItem()).toString());
                dto.addProperty("iconLabel", icon.getHoverName().getString());
                array.add(dto);
            });
        }
        return array;
    }
}
