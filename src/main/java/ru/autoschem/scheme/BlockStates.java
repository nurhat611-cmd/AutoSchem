package ru.autoschem.scheme;

public final class BlockStates {
    private BlockStates() {
    }

    public static String blockId(String state) {
        int properties = state.indexOf('[');
        String id = properties < 0 ? state : state.substring(0, properties);
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    public static boolean isAir(String state) {
        return switch (blockId(state)) {
            case "minecraft:air", "minecraft:cave_air", "minecraft:void_air" -> true;
            default -> false;
        };
    }

    public static boolean isStructureVoid(String state) {
        return blockId(state).equals("minecraft:structure_void");
    }
}
