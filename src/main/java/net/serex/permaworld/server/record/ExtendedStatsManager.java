package net.serex.permaworld.server.record;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.serex.permaworld.Permaworld;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class ExtendedStatsManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final Map<UUID, PlayerStats> CACHE = new ConcurrentHashMap<>();

    public static class PlayerStats {
        public double totalDamageDealt = 0;
        public double totalDamageTaken = 0;
        public double damageDealtSinceDeath = 0;
        public double damageTakenSinceDeath = 0;
        public double blocksFallen = 0;
        public double fallDamageReceived = 0;
        public int totalXpGained = 0;
        public int totalLevelsGained = 0;
        public int enchantedItemsCount = 0;
        
        public Map<String, Double> mobsDamage = new HashMap<>(); // Mob/Source ID -> Damage Taken
        public Map<String, Integer> enchantments = new HashMap<>(); // Enchantment -> Count
        public Set<String> discoveredStructures = new HashSet<>(); // Structure IDs
    }

    public static PlayerStats get(MinecraftServer server, UUID uuid) {
        return CACHE.computeIfAbsent(uuid, k -> load(server, k));
    }

    public static void save(MinecraftServer server, UUID uuid) {
        PlayerStats stats = CACHE.get(uuid);
        if (stats == null) return;
        try {
            Path path = getFilePath(server, uuid);
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(stats));
        } catch (Exception e) {
            Permaworld.LOGGER.error("Error al guardar extended stats para {}", uuid, e);
        }
    }

    private static PlayerStats load(MinecraftServer server, UUID uuid) {
        Path path = getFilePath(server, uuid);
        PlayerStats stats = null;
        if (Files.exists(path)) {
            try {
                String content = Files.readString(path);
                stats = GSON.fromJson(content, PlayerStats.class);
            } catch (Exception e) {
                Permaworld.LOGGER.error("Error al cargar extended stats para {}", uuid, e);
            }
        }
        if (stats == null) {
            stats = new PlayerStats();
        }

        // Cargar retroactivamente las estructuras descubiertas de records.jsonl
        if (stats.discoveredStructures.isEmpty() && server != null) {
            try {
                PermaworldRecordStore store = PermaworldRecordStore.forServer(server);
                for (JsonObject record : store.readPlayerRecords(uuid)) {
                    if (record.has("activityType") && "STRUCTURE_DISCOVERED".equals(record.get("activityType").getAsString())) {
                        if (record.has("metadata") && record.get("metadata").isJsonObject()) {
                            JsonObject meta = record.getAsJsonObject("metadata");
                            if (meta.has("structureId")) {
                                stats.discoveredStructures.add(meta.get("structureId").getAsString());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
        return stats;
    }

    private static Path getFilePath(MinecraftServer server, UUID uuid) {
        return server.getWorldPath(LevelResource.ROOT).resolve("permaworld").resolve("extended_stats").resolve(uuid.toString() + ".json");
    }

    public static void saveAll(MinecraftServer server) {
        for (UUID uuid : CACHE.keySet()) {
            save(server, uuid);
        }
    }

    public static void recordDamageDealt(MinecraftServer server, UUID uuid, double amount) {
        if (server == null) return;
        PlayerStats stats = get(server, uuid);
        stats.totalDamageDealt += amount;
        stats.damageDealtSinceDeath += amount;
        save(server, uuid);
    }

    public static void recordDamageTaken(MinecraftServer server, UUID uuid, double amount, String sourceName) {
        if (server == null) return;
        PlayerStats stats = get(server, uuid);
        stats.totalDamageTaken += amount;
        stats.damageTakenSinceDeath += amount;
        if (sourceName != null && !sourceName.isBlank()) {
            stats.mobsDamage.put(sourceName, stats.mobsDamage.getOrDefault(sourceName, 0.0) + amount);
        }
        save(server, uuid);
    }

    public static void recordFall(MinecraftServer server, UUID uuid, double blocks, double damage) {
        if (server == null) return;
        PlayerStats stats = get(server, uuid);
        stats.blocksFallen += blocks;
        stats.fallDamageReceived += damage;
        save(server, uuid);
    }

    public static void recordDeath(MinecraftServer server, UUID uuid) {
        if (server == null) return;
        PlayerStats stats = get(server, uuid);
        stats.damageDealtSinceDeath = 0;
        stats.damageTakenSinceDeath = 0;
        save(server, uuid);
    }

    public static void recordXpGained(MinecraftServer server, UUID uuid, int amount) {
        if (server == null) return;
        PlayerStats stats = get(server, uuid);
        stats.totalXpGained += amount;
        save(server, uuid);
    }

    public static void recordLevelsGained(MinecraftServer server, UUID uuid, int amount) {
        if (server == null) return;
        PlayerStats stats = get(server, uuid);
        stats.totalLevelsGained += amount;
        save(server, uuid);
    }

    public static void recordEnchantment(MinecraftServer server, UUID uuid, String enchantmentId) {
        if (server == null) return;
        PlayerStats stats = get(server, uuid);
        stats.enchantments.put(enchantmentId, stats.enchantments.getOrDefault(enchantmentId, 0) + 1);
        save(server, uuid);
    }

    public static void recordEnchantedItem(MinecraftServer server, UUID uuid) {
        if (server == null) return;
        PlayerStats stats = get(server, uuid);
        stats.enchantedItemsCount++;
        save(server, uuid);
    }

    public static void recordStructureDiscovered(MinecraftServer server, UUID uuid, String structureId) {
        if (server == null) return;
        PlayerStats stats = get(server, uuid);
        stats.discoveredStructures.add(structureId);
        save(server, uuid);
    }
}
