package net.serex.permaworld.client.feature.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lógica pura de ordenación del inventario.
 * <p>
 * Trabaja sobre una lista de {@link SortableSlot} indexada por slot. Devuelve una nueva
 * lista del mismo tamaño con los slots reordenados:
 * <ul>
 *   <li>Los slots cuyo índice esté en {@code lockedSlots} se mantienen tal cual.</li>
 *   <li>El resto se ordena por {@code itemId} (alfabético) y se fusionan stacks del mismo
 *       item respetando {@code maxStack}.</li>
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

        // 2. Fusiona stacks del mismo item respetando maxStack.
        List<SortableSlot> merged = mergeStacks(movable);

        // 3. Ordena por itemId DESCENDENTE (a petición del usuario: el orden
        //    ascendente sentía "al revés"). A igualdad de id, stacks más llenos primero.
        merged.sort(Comparator
                .comparing((SortableSlot s) -> s.itemId()).reversed()
                .thenComparing(Comparator.comparingInt(SortableSlot::count).reversed()));

        // 4. Coloca el resultado en los huecos libres; el resto quedan vacíos.
        for (int i = 0; i < freeIndices.size(); i++) {
            int idx = freeIndices.get(i);
            result[idx] = i < merged.size() ? merged.get(i) : SortableSlot.empty();
        }

        return List.of(result);
    }

    private static List<SortableSlot> mergeStacks(List<SortableSlot> slots) {
        Map<String, List<SortableSlot>> byItem = new HashMap<>();
        for (SortableSlot slot : slots) {
            byItem.computeIfAbsent(slot.itemId(), k -> new ArrayList<>()).add(slot);
        }

        List<SortableSlot> out = new ArrayList<>();
        for (Map.Entry<String, List<SortableSlot>> entry : byItem.entrySet()) {
            String id = entry.getKey();
            int total = entry.getValue().stream().mapToInt(SortableSlot::count).sum();
            int maxStack = entry.getValue().getFirst().maxStack();
            while (total > 0) {
                int take = Math.min(total, maxStack);
                out.add(new SortableSlot(id, take, maxStack));
                total -= take;
            }
        }
        return out;
    }
}
