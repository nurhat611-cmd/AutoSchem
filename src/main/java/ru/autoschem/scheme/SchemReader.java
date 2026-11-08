package ru.autoschem.scheme;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;

public final class SchemReader {
    private static final long MAX_NBT_BYTES = 64L * 1024 * 1024;
    private static final int MAX_PALETTE_SIZE = 65_536;

    public Scheme read(Path path) throws IOException {
        NbtCompound root = NbtIo.readCompressed(path, NbtSizeTracker.of(MAX_NBT_BYTES));
        NbtCompound schematic = root.getCompound("Schematic").orElse(root);
        int version = schematic.getInt("Version", 2);
        if (version < 1 || version > 3) {
            throw new IOException("Неподдерживаемая версия схемы: " + version);
        }

        int width = dimension(schematic, "Width");
        int height = dimension(schematic, "Height");
        int length = dimension(schematic, "Length");
        long volume = (long) width * height * length;
        if (volume > Scheme.MAX_BLOCKS) {
            throw new IOException("Схема слишком большая: " + volume + " блоков");
        }

        NbtCompound container = version == 3
                ? schematic.getCompound("Blocks").orElseThrow(() -> new IOException("Отсутствует Blocks"))
                : schematic;
        NbtCompound nbtPalette = container.getCompound("Palette")
                .orElseThrow(() -> new IOException("Отсутствует Palette"));
        if (nbtPalette.getKeys().isEmpty() || nbtPalette.getKeys().size() > MAX_PALETTE_SIZE) {
            throw new IOException("Недопустимый размер палитры");
        }

        Map<Integer, String> palette = new HashMap<>();
        for (String state : nbtPalette.getKeys()) {
            int id = nbtPalette.getInt(state)
                    .orElseThrow(() -> new IOException("Некорректный индекс палитры: " + state));
            if (id < 0 || state.isBlank() || palette.putIfAbsent(id, state) != null) {
                throw new IOException("Некорректная палитра: " + state);
            }
        }

        String dataKey = version == 3 ? "Data" : "BlockData";
        byte[] data = container.getByteArray(dataKey)
                .orElseThrow(() -> new IOException("Отсутствует " + dataKey));
        int[] blocks = decode(data, (int) volume, palette);
        return new Scheme(width, height, length, palette, blocks);
    }

    private int dimension(NbtCompound schematic, String name) throws IOException {
        int value = schematic.getShort(name)
                .orElseThrow(() -> new IOException("Отсутствует размер " + name)) & 0xffff;
        if (value == 0) {
            throw new IOException("Размер " + name + " равен нулю");
        }
        return value;
    }

    static int[] decode(byte[] data, int volume, Map<Integer, String> palette) throws IOException {
        int[] blocks = new int[volume];
        int offset = 0;
        for (int i = 0; i < volume; i++) {
            int value = 0;
            boolean complete = false;
            for (int part = 0; part < 5; part++) {
                if (offset >= data.length) {
                    throw new IOException("BlockData обрывается до конца схемы");
                }
                int current = data[offset++] & 0xff;
                if (part == 4 && (current & 0xf8) != 0) {
                    throw new IOException("Индекс палитры выходит за пределы int");
                }
                value |= (current & 0x7f) << (part * 7);
                if ((current & 0x80) == 0) {
                    complete = true;
                    break;
                }
            }
            if (!complete || !palette.containsKey(value)) {
                throw new IOException("Неизвестный или повреждённый индекс палитры: " + value);
            }
            blocks[i] = value;
        }
        if (offset != data.length) {
            throw new IOException("Лишние данные после последнего блока");
        }
        return blocks;
    }
}
