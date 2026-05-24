package net.serex.permaworld.client.feature.sort;

import java.util.Locale;

public enum SortCategory {
    TOOLS(0),
    WEAPONS(1),
    ARMOR(2),
    FOOD(3),
    MINERALS(4),
    REDSTONE(5),
    NATURE(6),
    BLOCKS(7),
    CONSUMABLES(8),
    MISC(9),
    EMPTY(10);

    private final int order;

    SortCategory(int order) {
        this.order = order;
    }

    public int order() {
        return order;
    }

    public static SortCategory fromItemId(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return EMPTY;
        }

        String id = itemId.toLowerCase(Locale.ROOT);
        if (matchesAny(id, "_pickaxe", "_axe", "_shovel", "_hoe", "shears", "fishing_rod", "brush", "flint_and_steel")) {
            return TOOLS;
        }
        if (id.endsWith(":bow") || matchesAny(id, "_sword", "crossbow", "trident", "mace", "arrow")) {
            return WEAPONS;
        }
        if (matchesAny(id, "_helmet", "_chestplate", "_leggings", "_boots", "shield", "elytra", "horse_armor")) {
            return ARMOR;
        }
        if (matchesAny(id, "apple", "bread", "beef", "porkchop", "mutton", "chicken", "rabbit", "cod", "salmon",
                "cookie", "cake", "stew", "soup", "carrot", "potato", "beetroot", "melon_slice", "berries")) {
            return FOOD;
        }
        if (matchesAny(id, "coal", "iron", "gold", "copper", "diamond", "emerald", "lapis", "quartz", "amethyst",
                "netherite", "_ore", "_ingot", "_nugget", "_gem")) {
            return MINERALS;
        }
        if (matchesAny(id, "redstone", "repeater", "comparator", "piston", "observer", "hopper", "dropper",
                "dispenser", "lever", "button", "pressure_plate", "tripwire", "daylight_detector")) {
            return REDSTONE;
        }
        if (matchesAny(id, "seed", "sapling", "leaves", "log", "stem", "flower", "tulip", "dandelion", "grass",
                "moss", "vine", "kelp", "bamboo", "cactus", "sugar_cane", "wheat", "pumpkin", "melon")) {
            return NATURE;
        }
        if (matchesAny(id, "potion", "bottle", "bucket", "ender_pearl", "firework", "splash_", "lingering_",
                "experience_bottle")) {
            return CONSUMABLES;
        }
        if (matchesAny(id, "stone", "dirt", "sand", "gravel", "planks", "brick", "block", "glass", "terracotta",
                "concrete", "wool", "slab", "stairs", "wall", "fence", "door", "trapdoor")) {
            return BLOCKS;
        }
        return MISC;
    }

    private static boolean matchesAny(String itemId, String... needles) {
        for (String needle : needles) {
            if (itemId.contains(needle)) {
                return true;
            }
        }
        return false;
    }
}
