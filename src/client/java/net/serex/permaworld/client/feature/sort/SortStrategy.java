package net.serex.permaworld.client.feature.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Lógica pura de ordenación del inventario.
 * <p>
 * Trabaja sobre una lista de {@link SortableSlot} indexada por slot. Devuelve una nueva
 * lista del mismo tamaño con los slots reordenados:
 * <ul>
 *   <li>Los slots cuyo índice esté en {@code lockedSlots} se mantienen tal cual.</li>
 *   <li>El resto se ordena según el modo elegido sin fusionar stacks.</li>
 *   <li>Los huecos vacíos quedan al final.</li>
 * </ul>
 * No emite paquetes: solo decide el estado objetivo. {@code InventorySorter} se encarga
 * de traducirlo a clicks Vanilla.
 */
public final class SortStrategy {

    private SortStrategy() {
    }

    /**
     * Calcula el estado objetivo del inventario tras ordenar.
     *
     * @param current     estado actual, indexado por slot
     * @param lockedSlots slots a ignorar (no se tocan)
     * @return nueva lista del mismo tamaño con el orden propuesto
     */
    public static List<SortableSlot> sort(List<SortableSlot> current, Set<Integer> lockedSlots) {
        return sort(current, lockedSlots, SortMode.NAME);
    }

    public static List<SortableSlot> sort(List<SortableSlot> current, Set<Integer> lockedSlots, SortMode mode) {
        int size = current.size();
        SortableSlot[] result = new SortableSlot[size];

        // 1. Slots bloqueados se quedan donde están.
        List<SortableSlot> movable = new ArrayList<>();
        List<Integer> freeIndices = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            SortableSlot slot = current.get(i);
            if (lockedSlots.contains(i)) {
                result[i] = slot;
            } else {
                freeIndices.add(i);
                if (!slot.isEmpty()) {
                    movable.add(slot);
                }
            }
        }

        // 2. Ordena sin fusionar stacks; el modo por nombre conserva el comportamiento actual.
        movable.sort(comparator(mode));

        // 3. Coloca el resultado en los huecos libres; el resto quedan vacíos.
        for (int i = 0; i < freeIndices.size(); i++) {
            int idx = freeIndices.get(i);
            result[idx] = i < movable.size() ? movable.get(i) : SortableSlot.empty();
        }

        return List.of(result);
    }

    private static Comparator<SortableSlot> comparator(SortMode mode) {
        return switch (mode) {
            case COUNT -> Comparator
                    .comparingInt(SortableSlot::count).reversed()
                    .thenComparing(Comparator.comparing((SortableSlot s) -> s.itemId()).reversed());
            case CATEGORY -> Comparator
                    .comparingInt((SortableSlot s) -> s.category().order())
                    .thenComparing(Comparator.comparing((SortableSlot s) -> s.itemId()).reversed())
                    .thenComparing(Comparator.comparingInt(SortableSlot::count).reversed());
            case NAME -> Comparator
                    .comparing((SortableSlot s) -> s.itemId()).reversed()
                    .thenComparing(Comparator.comparingInt(SortableSlot::count).reversed());
        };
    }
}
