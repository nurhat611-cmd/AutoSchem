package ru.autoschem.scheme;

import java.util.Map;
import java.util.Objects;

public final class Scheme {
    public static final int MAX_BLOCKS = 2_000_000;

    private final int width;
    private final int height;
    private final int length;
    private final Map<Integer, String> palette;
    private final int[] blocks;

    public Scheme(int width, int height, int length, Map<Integer, String> palette, int[] blocks) {
        long volume = (long) width * height * length;
        if (width <= 0 || height <= 0 || length <= 0 || volume > MAX_BLOCKS || volume != blocks.length) {
            throw new IllegalArgumentException("Недопустимые размеры схемы");
        }
        this.palette = Map.copyOf(Objects.requireNonNull(palette));
        if (this.palette.isEmpty()) {
            throw new IllegalArgumentException("Палитра схемы пуста");
        }
        this.blocks = blocks.clone();
        for (int id : this.blocks) {
            String state = this.palette.get(id);
            if (state == null || state.isBlank()) {
                throw new IllegalArgumentException("Неизвестный индекс палитры: " + id);
            }
        }
        this.width = width;
        this.height = height;
        this.length = length;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int length() {
        return length;
    }

    public int size() {
        return blocks.length;
    }

    public String blockState(int index) {
        return palette.get(blocks[index]);
    }
}
