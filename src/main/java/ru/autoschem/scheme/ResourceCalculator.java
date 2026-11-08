package ru.autoschem.scheme;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public final class ResourceCalculator {
    public Map<String, Integer> calculate(Scheme scheme) {
        Map<String, Integer> resources = new TreeMap<>();
        for (int i = 0; i < scheme.size(); i++) {
            String state = scheme.blockState(i);
            if (!BlockStates.isAir(state) && !BlockStates.isStructureVoid(state)) {
                resources.merge(BlockStates.blockId(state), 1, Integer::sum);
            }
        }
        return Collections.unmodifiableMap(resources);
    }

    public String format(Map<String, Integer> resources) {
        if (resources.isEmpty()) {
            return "нет блоков";
        }
        return resources.entrySet().stream()
                .map(entry -> entry.getKey() + " × " + entry.getValue())
                .collect(Collectors.joining(", "));
    }
}
