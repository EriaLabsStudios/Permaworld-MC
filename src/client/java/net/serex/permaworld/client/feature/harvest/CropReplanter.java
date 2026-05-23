package net.serex.permaworld.client.feature.harvest;

import java.util.List;
import java.util.Set;

/**
 * Lógica pura para localizar la semilla con la que replantar un cultivo.
 * <p>
 * No depende de tipos de Minecraft a propósito: se modela el inventario como una
 * lista de identificadores de item por slot, lo que permite tests unitarios sin
 * arrancar el cliente.
 */
public final class CropReplanter {

    private CropReplanter() {
    }

    /**
     * Busca el primer slot del inventario que contenga una semilla compatible
     * con el cultivo {@code cropId}. Prioriza la hotbar (slots 0..8) sobre el
     * resto del inventario para imitar lo que haría el jugador a mano.
     *
     * @param cropId    id del bloque cultivo (ej. {@code minecraft:wheat})
     * @param inventory items por slot (0..35); slots vacíos se representan con cadena vacía
     * @return índice del slot con la semilla, o {@code -1} si no hay
     */
    public static int findSeedSlot(String cropId, List<String> inventory) {
        Set<String> seeds = HarvestRegistry.seedsFor(cropId);
        if (seeds.isEmpty()) return -1;

        // Hotbar primero (0..8), luego storage (9..35).
        int size = inventory.size();
        int hotbarEnd = Math.min(9, size);
        for (int i = 0; i < hotbarEnd; i++) {
            if (seeds.contains(inventory.get(i))) return i;
        }
        for (int i = hotbarEnd; i < size; i++) {
            if (seeds.contains(inventory.get(i))) return i;
        }
        return -1;
    }
}
