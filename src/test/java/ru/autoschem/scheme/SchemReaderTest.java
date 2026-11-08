package ru.autoschem.scheme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SchemReaderTest {
    @TempDir
    Path directory;

    @Test
    void readsV2VarintsAndYzxOrdering() throws IOException {
        NbtCompound root = base(2, 2, 2, 2);
        NbtCompound palette = new NbtCompound();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:stone", 1);
        palette.putInt("minecraft:oak_log[axis=x]", 128);
        root.put("Palette", palette);
        root.putByteArray("BlockData", new byte[] {0, 1, (byte) 0x80, 1, 0, 1, 0, 0, 1});
        Scheme scheme = read(root);
        assertEquals(2, scheme.width());
        assertEquals(2, scheme.height());
        assertEquals(2, scheme.length());
        assertEquals("minecraft:oak_log[axis=x]", scheme.blockState(2));
        assertEquals("minecraft:stone", scheme.blockState(7));
    }

    @Test
    void readsV1PaletteAndBlockData() throws IOException {
        NbtCompound root = base(1, 2, 1, 1);
        NbtCompound palette = new NbtCompound();
        palette.putInt("minecraft:stone", 0);
        palette.putInt("minecraft:dirt", 1);
        root.put("Palette", palette);
        root.putByteArray("BlockData", new byte[] {0, 1});
        Scheme scheme = read(root);
        assertEquals("minecraft:stone", scheme.blockState(0));
        assertEquals("minecraft:dirt", scheme.blockState(1));
    }

    @Test
    void readsWrappedV3() throws IOException {
        NbtCompound schematic = base(3, 2, 1, 1);
        NbtCompound blocks = new NbtCompound();
        NbtCompound palette = new NbtCompound();
        palette.putInt("minecraft:air", 0);
        palette.putInt("minecraft:gold_block", 1);
        blocks.put("Palette", palette);
        blocks.putByteArray("Data", new byte[] {1, 0});
        schematic.put("Blocks", blocks);
        NbtCompound root = new NbtCompound();
        root.put("Schematic", schematic);
        Scheme scheme = read(root);
        assertEquals("minecraft:gold_block", scheme.blockState(0));
        assertEquals("minecraft:air", scheme.blockState(1));
    }

    @Test
    void treatsDimensionsAsUnsignedShorts() throws IOException {
        NbtCompound root = base(2, 40_000, 1, 1);
        NbtCompound palette = new NbtCompound();
        palette.putInt("minecraft:stone", 0);
        root.put("Palette", palette);
        root.putByteArray("BlockData", new byte[40_000]);
        assertEquals(40_000, read(root).width());
    }

    @Test
    void rejectsTruncatedUnknownAndExtraBlockData() throws IOException {
        NbtCompound root = base(2, 1, 1, 1);
        NbtCompound palette = new NbtCompound();
        palette.putInt("minecraft:stone", 0);
        root.put("Palette", palette);
        for (byte[] data : new byte[][] {{(byte) 0x80}, {1}, {0, 0},
                {(byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 8}}) {
            root.putByteArray("BlockData", data);
            assertThrows(IOException.class, () -> read(root));
        }
    }

    @Test
    void rejectsDuplicatePaletteIdsAndOversizedDimensions() throws IOException {
        NbtCompound root = base(2, 1, 1, 1);
        NbtCompound palette = new NbtCompound();
        palette.putInt("minecraft:stone", 0);
        palette.putInt("minecraft:dirt", 0);
        root.put("Palette", palette);
        root.putByteArray("BlockData", new byte[] {0});
        assertThrows(IOException.class, () -> read(root));
        root.putShort("Width", (short) 2_001);
        root.putShort("Height", (short) 1_000);
        assertThrows(IOException.class, () -> read(root));
    }

    private Scheme read(NbtCompound root) throws IOException {
        Path path = directory.resolve("test.schem");
        NbtIo.writeCompressed(root, path);
        return new SchemReader().read(path);
    }

    private NbtCompound base(int version, int width, int height, int length) {
        NbtCompound root = new NbtCompound();
        root.putInt("Version", version);
        root.putShort("Width", (short) width);
        root.putShort("Height", (short) height);
        root.putShort("Length", (short) length);
        return root;
    }
}
