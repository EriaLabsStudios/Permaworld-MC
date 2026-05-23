package net.serex.permaworld.client.feature.harvest;

import java.util.Map;
import java.util.Set;

/**
 * Mapeo cultivo → semillas válidas para replantar.
 * <p>
 * Mantenido como tabla explícita en vez de derivarlo del bloque: en Vanilla la
 * relación cultivo↔semilla no es trivial (ej. trigo se planta con semillas de
 * trigo, no con trigo). Si en el futuro se añaden cultivos modeados, este
 * mapping se podrá extender.
 */
public final class HarvestRegistry {

    private static final Map<String, Set<String>> CROP_TO_SEEDS = Map.of(
            "minecraft:wheat",      Set.of("minecraft:wheat_seeds"),
            "minecraft:carrots",    Set.of("minecraft:carrot"),
            "minecraft:potatoes",   Set.of("minecraft:potato"),
            "minecraft:beetroots",  Set.of("minecraft:beetroot_seeds")
    );

    private HarvestRegistry() {
    }

    /** Semillas válidas para un cultivo, o vacío si el cultivo no está soportado. */
    public static Set<String> seedsFor(String cropId) {
        return CROP_TO_SEEDS.getOrDefault(cropId, Set.of());
    }

    /** ¿Está el cultivo soportado por la feature? */
    public static boolean isSupported(String cropId) {
        return CROP_TO_SEEDS.containsKey(cropId);
    }
}
