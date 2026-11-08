package ru.autoschem.scheme;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ResourceCalculatorTest {
    @Test
    void groupsPropertiesAndIgnoresAirAndStructureVoid() {
        Scheme scheme = new Scheme(6, 1, 1, Map.of(
                0, "minecraft:oak_log[axis=x]",
                1, "minecraft:oak_log[axis=y]",
                2, "stone",
                3, "minecraft:air",
                4, "minecraft:cave_air",
                5, "minecraft:structure_void"), new int[] {0, 1, 2, 3, 4, 5});
        ResourceCalculator calculator = new ResourceCalculator();
        Map<String, Integer> result = calculator.calculate(scheme);
        assertEquals(Map.of("minecraft:oak_log", 2, "minecraft:stone", 1), result);
        assertEquals("minecraft:oak_log × 2, minecraft:stone × 1", calculator.format(result));
    }
}
