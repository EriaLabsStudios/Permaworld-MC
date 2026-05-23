package net.serex.permaworld.client.feature.sort;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SortStrategyTest {

    private static SortableSlot apple(int count) {
        return new SortableSlot("minecraft:apple", count, 64);
    }

    private static SortableSlot stick(int count) {
        return new SortableSlot("minecraft:stick", count, 64);
    }

    private static SortableSlot pickaxe() {
        return new SortableSlot("minecraft:iron_pickaxe", 1, 1);
    }

    private static List<SortableSlot> inv(SortableSlot... slots) {
        return new ArrayList<>(List.of(slots));
    }

    @Test
    void ordenaAlfabeticamenteYAgrupaStacks() {
        List<SortableSlot> input = inv(
                stick(10),
                SortableSlot.empty(),
                apple(20),
                stick(5),
                apple(40)
        );

        List<SortableSlot> result = SortStrategy.sort(input, Set.of());

        assertEquals(5, result.size());
        // Orden DESCENDENTE por itemId: stick antes que apple.
        // 15 sticks → un stack de 15, y 60 manzanas → un stack de 60.
        assertEquals("minecraft:stick", result.get(0).itemId());
        assertEquals(15, result.get(0).count());
        assertEquals("minecraft:apple", result.get(1).itemId());
        assertEquals(60, result.get(1).count());
        // El resto vacío.
        for (int i = 2; i < result.size(); i++) {
            assertTrue(result.get(i).isEmpty(), "slot " + i + " debería estar vacío");
        }
    }

    @Test
    void respetaSlotsBloqueados() {
        List<SortableSlot> input = inv(
                apple(5),
                pickaxe(),       // slot 1 bloqueado
                stick(3),
                SortableSlot.empty()
        );

        List<SortableSlot> result = SortStrategy.sort(input, Set.of(1));

        // El pico sigue en el slot 1 sin tocarse.
        assertEquals("minecraft:iron_pickaxe", result.get(1).itemId());
        // El resto reordenado DESCENDENTE: stick antes que apple.
        assertEquals("minecraft:stick", result.get(0).itemId());
        assertEquals(3, result.get(0).count());
        assertEquals("minecraft:apple", result.get(2).itemId());
        assertEquals(5, result.get(2).count());
        assertTrue(result.get(3).isEmpty());
    }

    @Test
    void respetaMaxStackAlFusionar() {
        // 100 manzanas en 3 stacks; maxStack = 64 → debe partir en 64 + 36.
        List<SortableSlot> input = inv(apple(40), apple(40), apple(20));

        List<SortableSlot> result = SortStrategy.sort(input, Set.of());

        assertEquals(64, result.get(0).count());
        assertEquals(36, result.get(1).count());
        assertTrue(result.get(2).isEmpty());
    }

    @Test
    void inventarioVacioDevuelveTodoVacio() {
        List<SortableSlot> input = inv(SortableSlot.empty(), SortableSlot.empty());

        List<SortableSlot> result = SortStrategy.sort(input, Set.of());

        assertEquals(2, result.size());
        assertTrue(result.get(0).isEmpty());
        assertTrue(result.get(1).isEmpty());
    }

    @Test
    void slotsBloqueadosAlBordeNoSeMueven() {
        // Bloqueados los slots 0 y último; el del medio se ordena.
        List<SortableSlot> input = inv(
                pickaxe(),
                stick(2),
                apple(1),
                pickaxe()
        );

        List<SortableSlot> result = SortStrategy.sort(input, Set.of(0, 3));

        assertEquals("minecraft:iron_pickaxe", result.get(0).itemId());
        assertEquals("minecraft:iron_pickaxe", result.get(3).itemId());
        // Entre medias, orden DESCENDENTE: stick antes que apple.
        assertEquals("minecraft:stick", result.get(1).itemId());
        assertEquals("minecraft:apple", result.get(2).itemId());
    }
}
