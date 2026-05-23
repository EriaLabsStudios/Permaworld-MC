package net.serex.permaworld.client.feature.sort;

/**
 * Representación inmutable y testeable del contenido de un slot para la lógica de orden.
 * No depende de tipos de Minecraft a propósito: facilita los tests unitarios.
 *
 * @param itemId   identificador del item (ej. {@code minecraft:apple}); vacío si el slot está vacío
 * @param count    número de items en el stack; 0 si está vacío
 * @param maxStack tamaño máximo del stack para ese item (típicamente 1, 16 o 64)
 */
public record SortableSlot(String itemId, int count, int maxStack) {

    private static final SortableSlot EMPTY = new SortableSlot("", 0, 64);

    public static SortableSlot empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return count <= 0 || itemId.isEmpty();
    }
}
