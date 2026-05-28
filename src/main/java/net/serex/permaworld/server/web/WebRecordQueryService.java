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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.ListTag;
import net.serex.permaworld.server.record.InventorySnapshotService;

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
        
        if ("ALL".equalsIgnoreCase(normalized) || "CURRENT_STATE".equalsIgnoreCase(normalized)) {
            currentStateRecord(playerId).ifPresent(record -> {
                JsonObject dto = WebDtos.recordCard(record);
                enrichRecord(dto, record);
                result.add(dto);
            });
        }

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
        if ("current-state".equals(recordId)) {
            return currentStateRecord(playerId).map(record -> {
                JsonObject dto = WebDtos.recordDetail(record);
                enrichRecord(dto, record);
                return dto;
            });
        }
        return store.findPlayerRecord(playerId, recordId).map(record -> {
            JsonObject dto = WebDtos.recordDetail(record);
            enrichRecord(dto, record);
            return dto;
        });
    }

    public Optional<JsonObject> currentStateRecord(UUID playerId) {
        if (server == null) {
            return Optional.empty();
        }
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            JsonObject snapshot = InventorySnapshotService.captureSnapshot(server, player, "CURRENT_STATE");
            snapshot.addProperty("id", "current-state");
            snapshot.addProperty("reason", "CURRENT_STATE");
            return Optional.of(snapshot);
        } else {
            Path path = server.getWorldPath(LevelResource.ROOT).resolve("playerdata").resolve(playerId.toString() + ".dat");
            if (!Files.exists(path)) {
                return Optional.empty();
            }
            try {
                CompoundTag tag = NbtIo.readCompressed(Files.newInputStream(path), NbtAccounter.create(999999999L));
                JsonObject snapshot = new JsonObject();
                snapshot.addProperty("schemaVersion", 1);
                snapshot.addProperty("type", "inventory_snapshot");
                snapshot.addProperty("id", "current-state");
                snapshot.addProperty("timestamp", Instant.now().toString());
                snapshot.addProperty("reason", "CURRENT_STATE");
                snapshot.addProperty("playerUuid", playerId.toString());
                
                String offlineName = playerId.toString();
                try {
                    List<JsonObject> records = store.readPlayerRecords(playerId);
                    offlineName = records.stream()
                            .filter(r -> r.has("playerName"))
                            .map(r -> r.get("playerName").getAsString())
                            .findFirst()
                            .orElse(playerId.toString());
                } catch (Exception ignored) {}
                snapshot.addProperty("playerName", offlineName);
                
                String dimension = tag.getString("Dimension").orElse("minecraft:overworld");
                snapshot.addProperty("dimension", dimension);
                
                JsonObject position = new JsonObject();
                tag.getList("Pos").ifPresent(posList -> {
                    try {
                        position.addProperty("x", posList.getDouble(0).orElse(0.0));
                        position.addProperty("y", posList.getDouble(1).orElse(0.0));
                        position.addProperty("z", posList.getDouble(2).orElse(0.0));
                    } catch (Exception ignored) {}
                });
                if (!position.has("x")) {
                    position.addProperty("x", 0.0);
                    position.addProperty("y", 0.0);
                    position.addProperty("z", 0.0);
                }
                snapshot.add("position", position);
                
                snapshot.addProperty("health", tag.getFloat("Health").orElse(20.0f));
                snapshot.addProperty("foodLevel", tag.getInt("foodLevel").orElse(20));
                snapshot.addProperty("experienceLevel", tag.getInt("XpLevel").orElse(0));
                snapshot.addProperty("totalExperience", tag.getInt("XpTotal").orElse(0));
                
                String gameMode = "survival";
                int type = tag.getInt("playerGameType").orElse(0);
                gameMode = switch (type) {
                    case 1 -> "creative";
                    case 2 -> "adventure";
                    case 3 -> "spectator";
                    default -> "survival";
                };
                snapshot.addProperty("gameMode", gameMode);
                
                JsonArray items = new JsonArray();
                var registryAccess = server.registryAccess();
                
                tag.getList("Inventory").ifPresent(invList -> {
                    for (int i = 0; i < invList.size(); i++) {
                        invList.getCompound(i).ifPresent(itemTag -> {
                            ItemStack stack = ItemStack.CODEC.parse(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registryAccess), itemTag)
                                    .result()
                                    .orElse(ItemStack.EMPTY);
                            if (!stack.isEmpty()) {
                                byte rawSlot = itemTag.getByte("Slot").orElse((byte) 0);
                                int slotNum = rawSlot & 0xFF;
                                String section = "inventory";
                                int finalSlot = slotNum;
                                
                                if (slotNum >= 0 && slotNum < 9) {
                                    section = "inventory";
                                    finalSlot = slotNum;
                                } else if (slotNum >= 9 && slotNum < 36) {
                                    section = "inventory";
                                    finalSlot = slotNum;
                                } else if (slotNum >= 100 && slotNum < 104) {
                                    section = "armor";
                                    finalSlot = slotNum - 100;
                                } else if (slotNum == -106) {
                                    section = "offhand";
                                    finalSlot = 0;
                                }
                                
                                net.serex.permaworld.server.record.ItemStackRecordCodec.encode(section, finalSlot, stack, registryAccess)
                                        .ifPresent(items::add);
                            }
                        });
                    }
                });
                
                tag.getList("EnderItems").ifPresent(enderList -> {
                    for (int i = 0; i < enderList.size(); i++) {
                        enderList.getCompound(i).ifPresent(itemTag -> {
                            ItemStack stack = ItemStack.CODEC.parse(net.minecraft.resources.RegistryOps.create(net.minecraft.nbt.NbtOps.INSTANCE, registryAccess), itemTag)
                                    .result()
                                    .orElse(ItemStack.EMPTY);
                            if (!stack.isEmpty()) {
                                byte rawSlot = itemTag.getByte("Slot").orElse((byte) 0);
                                int slotNum = rawSlot & 0xFF;
                                net.serex.permaworld.server.record.ItemStackRecordCodec.encode("ender_chest", slotNum, stack, registryAccess)
                                        .ifPresent(items::add);
                            }
                        });
                    }
                });
                
                snapshot.addProperty("itemCount", items.size());
                snapshot.add("items", items);
                return Optional.of(snapshot);
            } catch (Exception e) {
                return Optional.empty();
            }
        }
    }

    public JsonObject playerStats(UUID playerId) {
        JsonObject payload = new JsonObject();
        
        // Extract history metadata from JSONL records
        try {
            List<JsonObject> records = store.readPlayerRecords(playerId);
            if (!records.isEmpty()) {
                JsonObject oldest = records.stream()
                        .min(Comparator.comparing(r -> r.get("timestamp").getAsString()))
                        .orElse(records.get(0));
                payload.addProperty("firstJoined", oldest.get("timestamp").getAsString());

                JsonObject newest = records.stream()
                        .max(Comparator.comparing(r -> r.get("timestamp").getAsString()))
                        .orElse(records.get(records.size() - 1));
                payload.addProperty("lastConnected", newest.get("timestamp").getAsString());

                if (newest.has("position") && newest.get("position").isJsonObject()) {
                    JsonObject posObj = newest.getAsJsonObject("position");
                    double x = posObj.has("x") ? posObj.get("x").getAsDouble() : 0.0;
                    double y = posObj.has("y") ? posObj.get("y").getAsDouble() : 0.0;
                    double z = posObj.has("z") ? posObj.get("z").getAsDouble() : 0.0;
                    String dim = newest.has("dimension") ? newest.get("dimension").getAsString() : "unknown";
                    payload.addProperty("lastKnownPosition", String.format(java.util.Locale.ROOT, "%.1f, %.1f, %.1f (%s)", x, y, z, dim));
                } else if (newest.has("dimension")) {
                    payload.addProperty("lastKnownPosition", newest.get("dimension").getAsString());
                } else {
                    payload.addProperty("lastKnownPosition", "unknown");
                }
            } else {
                payload.addProperty("firstJoined", "unknown");
                payload.addProperty("lastConnected", "unknown");
                payload.addProperty("lastKnownPosition", "unknown");
            }
        } catch (Exception e) {
            payload.addProperty("firstJoined", "unknown");
            payload.addProperty("lastConnected", "unknown");
            payload.addProperty("lastKnownPosition", "unknown");
        }

        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(playerId);
        if (player == null) {
            JsonObject offlineStats = loadOfflineStats(playerId);
            if (offlineStats.size() > 0) {
                payload.addProperty("available", true);
                payload.addProperty("online", false);
                
                String offlineName = playerId.toString();
                try {
                    List<JsonObject> records = store.readPlayerRecords(playerId);
                    offlineName = records.stream()
                            .filter(r -> r.has("playerName"))
                            .map(r -> r.get("playerName").getAsString())
                            .findFirst()
                            .orElse(playerId.toString());
                } catch (Exception ignored) {}
                payload.addProperty("playerName", offlineName);

                JsonArray highlights = new JsonArray();
                int deaths = getOfflineStatValue(offlineStats, "minecraft:custom", BuiltInRegistries.CUSTOM_STAT.getKey(Stats.DEATHS).toString());
                int playTime = getOfflineStatValue(offlineStats, "minecraft:custom", BuiltInRegistries.CUSTOM_STAT.getKey(Stats.PLAY_TIME).toString());
                int timeSinceDeath = getOfflineStatValue(offlineStats, "minecraft:custom", BuiltInRegistries.CUSTOM_STAT.getKey(Stats.TIME_SINCE_DEATH).toString());
                int mobKills = getOfflineStatValue(offlineStats, "minecraft:custom", BuiltInRegistries.CUSTOM_STAT.getKey(Stats.MOB_KILLS).toString());
                int distance = totalDistanceOffline(offlineStats);
                int foodEaten = getFoodEatenOffline(offlineStats);
                int toolsBroken = getToolsBrokenOffline(offlineStats);
                int bred = getOfflineStatValue(offlineStats, "minecraft:custom", BuiltInRegistries.CUSTOM_STAT.getKey(Stats.ANIMALS_BRED).toString());
                int hostileKills = getHostileKillsOffline(offlineStats);
                int passiveKills = getPassiveKillsOffline(offlineStats);
                int structuresDiscovered = getUniqueStructuresDiscovered(playerId);

                highlights.add(WebDtos.stat("deaths", "Deaths", deaths, Integer.toString(deaths)));
                highlights.add(WebDtos.stat("play_time", "Play Time", playTime, formatTicks(playTime)));
                highlights.add(WebDtos.stat("time_since_death", "Time Since Death", timeSinceDeath, formatTicks(timeSinceDeath)));
                highlights.add(WebDtos.stat("days_survived", "Days Survived", timeSinceDeath / 24000, (timeSinceDeath / 24000) + " dias"));
                highlights.add(WebDtos.stat("distance", "Distance", distance, formatDistance(distance)));
                highlights.add(WebDtos.stat("mob_kills", "Mob Kills", mobKills, Integer.toString(mobKills)));
                highlights.add(WebDtos.stat("food_eaten", "Food Eaten", foodEaten, Integer.toString(foodEaten)));
                highlights.add(WebDtos.stat("animals_bred", "Animals Bred", bred, Integer.toString(bred)));
                highlights.add(WebDtos.stat("hostile_kills", "Hostile Mobs Killed", hostileKills, Integer.toString(hostileKills)));
                highlights.add(WebDtos.stat("passive_kills", "Passive Mobs Killed", passiveKills, Integer.toString(passiveKills)));
                highlights.add(WebDtos.stat("tools_broken", "Tools Broken", toolsBroken, Integer.toString(toolsBroken)));
                highlights.add(WebDtos.stat("structures_discovered", "Structures Visited", structuresDiscovered, Integer.toString(structuresDiscovered)));
                payload.add("highlights", highlights);

                payload.add("blocksMined", topBlocksOffline(offlineStats));
                payload.add("itemsCrafted", topItemsOffline(offlineStats, "minecraft:crafted"));
                payload.add("itemsPickedUp", topItemsOffline(offlineStats, "minecraft:picked_up"));
                payload.add("entitiesKilled", topEntitiesOffline(offlineStats));

                addExtendedStatsToPayload(payload, playerId);

                return payload;
            }

            payload.addProperty("available", false);
            payload.addProperty("online", false);
            payload.addProperty("message", "Statistics are available when the player is online.");
            
            String offlineName = playerId.toString();
            try {
                List<JsonObject> records = store.readPlayerRecords(playerId);
                offlineName = records.stream()
                        .filter(r -> r.has("playerName"))
                        .map(r -> r.get("playerName").getAsString())
                        .findFirst()
                        .orElse(playerId.toString());
            } catch (Exception ignored) {}
            payload.addProperty("playerName", offlineName);
            return payload;
        }

        ServerStatsCounter stats = server.getPlayerList().getPlayerStats(player);
        payload.addProperty("available", true);
        payload.addProperty("online", true);
        payload.addProperty("playerName", player.getName().getString());

        JsonArray highlights = new JsonArray();
        int foodEaten = 0;
        int toolsBroken = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            if (new ItemStack(item).getComponents().has(net.minecraft.core.component.DataComponents.FOOD)) {
                foodEaten += stats.getValue(Stats.ITEM_USED, item);
            }
            toolsBroken += stats.getValue(Stats.ITEM_BROKEN, item);
        }
        int bred = stats.getValue(Stats.CUSTOM, Stats.ANIMALS_BRED);
        int hostileKills = getHostileKills(stats);
        int passiveKills = getPassiveKills(stats);
        int structuresDiscovered = getUniqueStructuresDiscovered(playerId);

        highlights.add(WebDtos.stat("deaths", "Deaths", stats.getValue(Stats.CUSTOM, Stats.DEATHS), Integer.toString(stats.getValue(Stats.CUSTOM, Stats.DEATHS))));
        highlights.add(WebDtos.stat("play_time", "Play Time", stats.getValue(Stats.CUSTOM, Stats.PLAY_TIME), formatTicks(stats.getValue(Stats.CUSTOM, Stats.PLAY_TIME))));
        highlights.add(WebDtos.stat("time_since_death", "Time Since Death", stats.getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH), formatTicks(stats.getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH))));
        highlights.add(WebDtos.stat("days_survived", "Days Survived", stats.getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH) / 24000, (stats.getValue(Stats.CUSTOM, Stats.TIME_SINCE_DEATH) / 24000) + " dias"));
        highlights.add(WebDtos.stat("distance", "Distance", totalDistance(stats), formatDistance(totalDistance(stats))));
        highlights.add(WebDtos.stat("mob_kills", "Mob Kills", stats.getValue(Stats.CUSTOM, Stats.MOB_KILLS), Integer.toString(stats.getValue(Stats.CUSTOM, Stats.MOB_KILLS))));
        highlights.add(WebDtos.stat("food_eaten", "Food Eaten", foodEaten, Integer.toString(foodEaten)));
        highlights.add(WebDtos.stat("animals_bred", "Animals Bred", bred, Integer.toString(bred)));
        highlights.add(WebDtos.stat("hostile_kills", "Hostile Mobs Killed", hostileKills, Integer.toString(hostileKills)));
        highlights.add(WebDtos.stat("passive_kills", "Passive Mobs Killed", passiveKills, Integer.toString(passiveKills)));
        highlights.add(WebDtos.stat("tools_broken", "Tools Broken", toolsBroken, Integer.toString(toolsBroken)));
        highlights.add(WebDtos.stat("structures_discovered", "Structures Visited", structuresDiscovered, Integer.toString(structuresDiscovered)));
        payload.add("highlights", highlights);

        payload.add("blocksMined", topBlocks(stats));
        payload.add("itemsCrafted", topItems(stats, Stats.ITEM_CRAFTED));
        payload.add("itemsPickedUp", topItems(stats, Stats.ITEM_PICKED_UP));
        payload.add("entitiesKilled", topEntities(stats));

        addExtendedStatsToPayload(payload, playerId);

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
        dto.addProperty("advancementFrame", display.getType().name());
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

    private JsonObject loadOfflineStats(UUID playerId) {
        JsonObject result = new JsonObject();
        if (server == null) {
            return result;
        }
        Path path = server.getWorldPath(LevelResource.ROOT).resolve("stats").resolve(playerId.toString() + ".json");
        if (!Files.exists(path)) {
            try {
                List<JsonObject> records = store.readPlayerRecords(playerId);
                for (int i = records.size() - 1; i >= 0; i--) {
                    JsonObject record = records.get(i);
                    if (record.has("stats") && record.get("stats").isJsonObject()) {
                        JsonObject customStats = new JsonObject();
                        JsonObject recordStats = record.getAsJsonObject("stats");
                        
                        customStats.addProperty("minecraft:deaths", recordStats.has("deaths") ? recordStats.get("deaths").getAsInt() : 0);
                        customStats.addProperty("minecraft:play_time", recordStats.has("playTime") ? recordStats.get("playTime").getAsInt() : 0);
                        customStats.addProperty("minecraft:time_since_death", recordStats.has("timeSinceDeath") ? recordStats.get("timeSinceDeath").getAsInt() : 0);
                        customStats.addProperty("minecraft:mob_kills", recordStats.has("mobKills") ? recordStats.get("mobKills").getAsInt() : 0);
                        customStats.addProperty("minecraft:walk_one_cm", recordStats.has("distance") ? recordStats.get("distance").getAsInt() : 0);
                        
                        JsonObject mockStats = new JsonObject();
                        mockStats.add("minecraft:custom", customStats);
                        return mockStats;
                    }
                }
            } catch (Exception ignored) {}
            return result;
        }
        try {
            String content = Files.readString(path);
            JsonObject statsObj = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
            if (statsObj.has("stats") && statsObj.get("stats").isJsonObject()) {
                return statsObj.getAsJsonObject("stats");
            }
        } catch (Exception ignored) {}
        return result;
    }

    private int getOfflineStatValue(JsonObject offlineStats, String type, String key) {
        if (offlineStats == null) {
            return 0;
        }
        if (offlineStats.has(type) && offlineStats.get(type).isJsonObject()) {
            JsonObject typeObj = offlineStats.getAsJsonObject(type);
            if (typeObj.has(key) && typeObj.get(key).isJsonPrimitive()) {
                return typeObj.get(key).getAsInt();
            }
        }
        return 0;
    }

    private int totalDistanceOffline(JsonObject offlineStats) {
        Identifier[] distanceStats = {
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.WALK_ONE_CM),
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.SPRINT_ONE_CM),
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.SWIM_ONE_CM),
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.AVIATE_ONE_CM),
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.BOAT_ONE_CM),
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.MINECART_ONE_CM),
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.HORSE_ONE_CM),
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.WALK_UNDER_WATER_ONE_CM),
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.WALK_ON_WATER_ONE_CM),
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.FLY_ONE_CM),
                BuiltInRegistries.CUSTOM_STAT.getKey(Stats.CLIMB_ONE_CM)
        };
        int total = 0;
        for (Identifier statLoc : distanceStats) {
            if (statLoc != null) {
                total += getOfflineStatValue(offlineStats, "minecraft:custom", statLoc.toString());
            }
        }
        return total;
    }

    private JsonArray topBlocksOffline(JsonObject offlineStats) {
        List<JsonObject> top = new ArrayList<>();
        if (offlineStats.has("minecraft:mined") && offlineStats.get("minecraft:mined").isJsonObject()) {
            JsonObject minedObj = offlineStats.getAsJsonObject("minecraft:mined");
            for (String key : minedObj.keySet()) {
                try {
                    Block block = BuiltInRegistries.BLOCK.get(Identifier.parse(key)).map(ref -> ref.value()).orElse(null);
                    int value = minedObj.get(key).getAsInt();
                    if (block != null && value > 0) {
                        top.add(WebDtos.leaderboardEntry(key, block.getName().getString(), value));
                    }
                } catch (Exception ignored) {}
            }
        }
        top.sort(Comparator.comparingInt((JsonObject json) -> json.get("value").getAsInt()).reversed());
        return toArray(top, 8);
    }

    private JsonArray topItemsOffline(JsonObject offlineStats, String statType) {
        List<JsonObject> top = new ArrayList<>();
        if (offlineStats.has(statType) && offlineStats.get(statType).isJsonObject()) {
            JsonObject itemsObj = offlineStats.getAsJsonObject(statType);
            for (String key : itemsObj.keySet()) {
                try {
                    Item item = BuiltInRegistries.ITEM.get(Identifier.parse(key)).map(ref -> ref.value()).orElse(null);
                    int value = itemsObj.get(key).getAsInt();
                    if (item != null && value > 0) {
                        top.add(WebDtos.leaderboardEntry(key, new ItemStack(item).getHoverName().getString(), value));
                    }
                } catch (Exception ignored) {}
            }
        }
        top.sort(Comparator.comparingInt((JsonObject json) -> json.get("value").getAsInt()).reversed());
        return toArray(top, 8);
    }

    private JsonArray topEntitiesOffline(JsonObject offlineStats) {
        List<JsonObject> top = new ArrayList<>();
        if (offlineStats.has("minecraft:killed") && offlineStats.get("minecraft:killed").isJsonObject()) {
            JsonObject killedObj = offlineStats.getAsJsonObject("minecraft:killed");
            for (String key : killedObj.keySet()) {
                try {
                    EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(key)).map(ref -> ref.value()).orElse(null);
                    int value = killedObj.get(key).getAsInt();
                    if (entityType != null && value > 0) {
                        top.add(WebDtos.leaderboardEntry(key, entityType.getDescription().getString(), value));
                    }
                } catch (Exception ignored) {}
            }
        }
        top.sort(Comparator.comparingInt((JsonObject json) -> json.get("value").getAsInt()).reversed());
        return toArray(top, 8);
    }

    private int getHostileKills(ServerStatsCounter stats) {
        int kills = 0;
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            if (entityType.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER) {
                kills += stats.getValue(Stats.ENTITY_KILLED, entityType);
            }
        }
        return kills;
    }

    private int getPassiveKills(ServerStatsCounter stats) {
        int kills = 0;
        for (EntityType<?> entityType : BuiltInRegistries.ENTITY_TYPE) {
            if (entityType.getCategory() != net.minecraft.world.entity.MobCategory.MONSTER 
                && entityType.getCategory() != net.minecraft.world.entity.MobCategory.MISC) {
                kills += stats.getValue(Stats.ENTITY_KILLED, entityType);
            }
        }
        return kills;
    }

    private int getFoodEatenOffline(JsonObject offlineStats) {
        int total = 0;
        if (offlineStats.has("minecraft:used") && offlineStats.get("minecraft:used").isJsonObject()) {
            JsonObject usedObj = offlineStats.getAsJsonObject("minecraft:used");
            for (String key : usedObj.keySet()) {
                try {
                    Item item = BuiltInRegistries.ITEM.get(Identifier.parse(key)).map(ref -> ref.value()).orElse(null);
                    if (item != null && new ItemStack(item).getComponents().has(net.minecraft.core.component.DataComponents.FOOD)) {
                        total += usedObj.get(key).getAsInt();
                    }
                } catch (Exception ignored) {}
            }
        }
        return total;
    }

    private int getToolsBrokenOffline(JsonObject offlineStats) {
        int total = 0;
        if (offlineStats.has("minecraft:broken") && offlineStats.get("minecraft:broken").isJsonObject()) {
            JsonObject brokenObj = offlineStats.getAsJsonObject("minecraft:broken");
            for (String key : brokenObj.keySet()) {
                try {
                    total += brokenObj.get(key).getAsInt();
                } catch (Exception ignored) {}
            }
        }
        return total;
    }

    private int getHostileKillsOffline(JsonObject offlineStats) {
        int total = 0;
        if (offlineStats.has("minecraft:killed") && offlineStats.get("minecraft:killed").isJsonObject()) {
            JsonObject killedObj = offlineStats.getAsJsonObject("minecraft:killed");
            for (String key : killedObj.keySet()) {
                try {
                    EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(key)).map(ref -> ref.value()).orElse(null);
                    if (entityType != null && entityType.getCategory() == net.minecraft.world.entity.MobCategory.MONSTER) {
                        total += killedObj.get(key).getAsInt();
                    }
                } catch (Exception ignored) {}
            }
        }
        return total;
    }

    private int getPassiveKillsOffline(JsonObject offlineStats) {
        int total = 0;
        if (offlineStats.has("minecraft:killed") && offlineStats.get("minecraft:killed").isJsonObject()) {
            JsonObject killedObj = offlineStats.getAsJsonObject("minecraft:killed");
            for (String key : killedObj.keySet()) {
                try {
                    EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(Identifier.parse(key)).map(ref -> ref.value()).orElse(null);
                    if (entityType != null && entityType.getCategory() != net.minecraft.world.entity.MobCategory.MONSTER 
                        && entityType.getCategory() != net.minecraft.world.entity.MobCategory.MISC) {
                        total += killedObj.get(key).getAsInt();
                    }
                } catch (Exception ignored) {}
            }
        }
        return total;
    }

    private int getUniqueStructuresDiscovered(UUID playerId) {
        try {
            java.util.Set<String> discovered = new java.util.HashSet<>();
            for (JsonObject record : store.readPlayerRecords(playerId)) {
                if (record.has("activityType") && "STRUCTURE_DISCOVERED".equals(record.get("activityType").getAsString())) {
                    if (record.has("metadata") && record.get("metadata").isJsonObject()) {
                        JsonObject meta = record.getAsJsonObject("metadata");
                        if (meta.has("coords")) {
                            discovered.add(meta.get("coords").getAsString());
                        }
                    }
                }
            }
            return discovered.size();
        } catch (Exception ignored) {}
        return 0;
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
                dto.addProperty("frame", display.getType().name());
                ItemStack icon = display.getIcon().create();
                dto.addProperty("iconItemId", BuiltInRegistries.ITEM.getKey(icon.getItem()).toString());
                dto.addProperty("iconLabel", icon.getHoverName().getString());
                array.add(dto);
            });
        }
        return array;
    }

    private void addExtendedStatsToPayload(JsonObject payload, UUID playerId) {
        net.serex.permaworld.server.record.ExtendedStatsManager.PlayerStats ext = net.serex.permaworld.server.record.ExtendedStatsManager.get(server, playerId);
        JsonObject extJson = new JsonObject();
        extJson.addProperty("totalDamageDealt", ext.totalDamageDealt);
        extJson.addProperty("totalDamageTaken", ext.totalDamageTaken);
        extJson.addProperty("damageDealtSinceDeath", ext.damageDealtSinceDeath);
        extJson.addProperty("damageTakenSinceDeath", ext.damageTakenSinceDeath);
        extJson.addProperty("blocksFallen", ext.blocksFallen);
        extJson.addProperty("fallDamageReceived", ext.fallDamageReceived);
        extJson.addProperty("totalXpGained", ext.totalXpGained);
        extJson.addProperty("totalLevelsGained", ext.totalLevelsGained);
        extJson.addProperty("enchantedItemsCount", ext.enchantedItemsCount);
        
        // Mobs damage (sorted)
        com.google.gson.JsonArray mobsDamageArr = new com.google.gson.JsonArray();
        ext.mobsDamage.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Double>comparingByValue().reversed())
                .forEach(entry -> {
                    JsonObject entryJson = new JsonObject();
                    entryJson.addProperty("source", entry.getKey());
                    entryJson.addProperty("damage", entry.getValue());
                    mobsDamageArr.add(entryJson);
                });
        extJson.add("mobsDamage", mobsDamageArr);

        // Enchantments (sorted)
        com.google.gson.JsonArray enchantmentsArr = new com.google.gson.JsonArray();
        ext.enchantments.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Integer>comparingByValue().reversed())
                .forEach(entry -> {
                    JsonObject entryJson = new JsonObject();
                    entryJson.addProperty("enchantment", entry.getKey());
                    entryJson.addProperty("count", entry.getValue());
                    enchantmentsArr.add(entryJson);
                });
        extJson.add("enchantments", enchantmentsArr);

        // Discovered structures — dedupKey format: "structKey@coords"
        com.google.gson.JsonArray structuresArr = new com.google.gson.JsonArray();
        for (String dedupKey : ext.discoveredStructures) {
            int atIdx = dedupKey.lastIndexOf('@');
            com.google.gson.JsonObject sObj = new com.google.gson.JsonObject();
            if (atIdx >= 0) {
                sObj.addProperty("structureId", dedupKey.substring(0, atIdx));
                sObj.addProperty("coords", dedupKey.substring(atIdx + 1));
            } else {
                // retrocompatibilidad con claves antiguas sin @coords
                sObj.addProperty("structureId", dedupKey);
                sObj.addProperty("coords", "");
            }
            structuresArr.add(sObj);
        }
        extJson.add("discoveredStructures", structuresArr);

        payload.add("extendedStats", extJson);
    }

    public JsonArray allStructures() {
        JsonArray array = new JsonArray();
        if (server == null) {
            return array;
        }
        try {
            net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> registry = server.overworld().registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
            List<String> ids = new ArrayList<>();
            for (net.minecraft.world.level.levelgen.structure.Structure structure : registry) {
                net.minecraft.resources.Identifier key = registry.getKey(structure);
                if (key != null) {
                    ids.add(key.toString());
                }
            }
            ids.sort(String::compareTo);
            ids.forEach(array::add);
        } catch (Exception e) {
            net.serex.permaworld.Permaworld.LOGGER.error("[Permaworld] Error listing all structures", e);
        }
        return array;
    }

    public boolean authorizeAdmin(String adminName) {
        if (adminName == null || adminName.isBlank() || server == null) return false;
        net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayerByName(adminName);
        return player != null && isAdmin(player, server);
    }

    public List<String> readLatestLogLines(int lineCount) {
        List<String> lines = new ArrayList<>();
        java.nio.file.Path logPath = java.nio.file.Path.of("logs/latest.log");
        if (java.nio.file.Files.exists(logPath)) {
            try {
                List<String> allLines = java.nio.file.Files.readAllLines(logPath);
                int size = allLines.size();
                for (int i = size - 1; i >= 0 && lines.size() < lineCount; i--) {
                    String line = allLines.get(i);
                    String lower = line.toLowerCase();
                    if (lower.contains("error") || 
                        lower.contains("warn") || 
                        lower.contains("fatal") || 
                        lower.contains("exception") || 
                        lower.contains("permaworld") || 
                        lower.contains("died") || 
                        lower.contains("completed the advancement") || 
                        lower.contains("has reached the advancement")) {
                        lines.add(0, line);
                    }
                }
            } catch (Exception e) {
                lines.add("Error al leer logs: " + e.getMessage());
            }
        } else {
            lines.add("Archivo logs/latest.log no encontrado.");
        }
        return lines;
    }

    public com.google.gson.JsonObject serverStatus() {
        com.google.gson.JsonObject res = new com.google.gson.JsonObject();
        if (server == null) {
            res.addProperty("online", false);
            return res;
        }
        res.addProperty("online", true);
        
        double tickTime = 0.0;
        try {
            long[] times = null;
            for (java.lang.reflect.Field field : server.getClass().getFields()) {
                if (field.getType() == long[].class) {
                    times = (long[]) field.get(server);
                    break;
                }
            }
            if (times == null) {
                for (java.lang.reflect.Field field : server.getClass().getDeclaredFields()) {
                    if (field.getType() == long[].class) {
                        field.setAccessible(true);
                        times = (long[]) field.get(server);
                        break;
                    }
                }
            }
            if (times == null && server.getClass().getSuperclass() != null) {
                for (java.lang.reflect.Field field : server.getClass().getSuperclass().getDeclaredFields()) {
                    if (field.getType() == long[].class) {
                        field.setAccessible(true);
                        times = (long[]) field.get(server);
                        break;
                    }
                }
            }
            if (times != null && times.length > 0) {
                long sum = 0;
                for (long t : times) {
                    sum += t;
                }
                tickTime = (double) sum / times.length * 1.0E-6D;
            }
        } catch (Exception ignored) {}
        
        double tps = Math.min(20.0, 1000.0 / Math.max(1.0, tickTime));
        res.addProperty("tps", Double.parseDouble(String.format(java.util.Locale.US, "%.2f", tps)));
        res.addProperty("tickTime", Double.parseDouble(String.format(java.util.Locale.US, "%.1f", tickTime)));
        
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        
        res.addProperty("maxMemoryMb", maxMemory / 1024 / 1024);
        res.addProperty("totalMemoryMb", totalMemory / 1024 / 1024);
        res.addProperty("usedMemoryMb", usedMemory / 1024 / 1024);
        
        com.google.gson.JsonArray playersArr = new com.google.gson.JsonArray();
        for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
            com.google.gson.JsonObject pObj = new com.google.gson.JsonObject();
            pObj.addProperty("name", p.getName().getString());
            pObj.addProperty("uuid", p.getUUID().toString());
            pObj.addProperty("dimension", p.level().dimension().identifier().toString());
            pObj.addProperty("x", (int) p.getX());
            pObj.addProperty("y", (int) p.getY());
            pObj.addProperty("z", (int) p.getZ());
            pObj.addProperty("health", Double.parseDouble(String.format(java.util.Locale.US, "%.1f", p.getHealth())));
            pObj.addProperty("maxHealth", (int) p.getMaxHealth());
            pObj.addProperty("ping", p.connection.latency());
            playersArr.add(pObj);
        }
        res.add("players", playersArr);
        
        return res;
    }
}
