package ru.autoschem.vsch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.autoschem.scheme.Scheme;

class VschFormatTest {
    @TempDir
    Path directory;

    @Test
    void writesMagicOutsideGzipAndRoundTripsInstructions() throws IOException {
        Scheme scheme = new Scheme(2, 1, 2, Map.of(
                0, "minecraft:stone",
                1, "minecraft:air",
                2, "minecraft:oak_log[axis=x]",
                3, "minecraft:structure_void"), new int[] {0, 1, 2, 3});
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new VschWriter().write(scheme, output);
        byte[] bytes = output.toByteArray();
        assertEquals('V', bytes[0]);
        assertEquals('S', bytes[1]);
        assertEquals('C', bytes[2]);
        assertEquals('H', bytes[3]);
        assertEquals(0x1f, bytes[4] & 0xff);
        assertEquals(0x8b, bytes[5] & 0xff);
        try (DataInputStream raw = new DataInputStream(new GZIPInputStream(
                new ByteArrayInputStream(bytes, 4, bytes.length - 4)))) {
            assertEquals(3, raw.readInt());
            assertEquals("PLACE", raw.readUTF());
            assertEquals(0, raw.readInt());
            assertEquals(0, raw.readInt());
            assertEquals(0, raw.readInt());
            assertEquals("minecraft:stone", raw.readUTF());
            assertEquals(1, raw.readInt());
        }
        List<Instruction> instructions = new VschReader().read(new ByteArrayInputStream(bytes));
        assertEquals(List.of(
                new Instruction(Instruction.Action.PLACE, 0, 0, 0, "minecraft:stone", 1),
                new Instruction(Instruction.Action.BREAK, 1, 0, 0, "minecraft:air", 1),
                new Instruction(Instruction.Action.PLACE, 0, 0, 1, "minecraft:oak_log[axis=x]", 1)), instructions);
    }

    @Test
    void rejectsMalformedFiles() throws IOException {
        assertThrows(IOException.class, () -> new VschReader().read(new ByteArrayInputStream(new byte[] {1, 2, 3, 4})));
        assertThrows(IOException.class, () -> new VschReader().read(new ByteArrayInputStream(fileWith("OTHER", 0, false))));
        assertThrows(IOException.class, () -> new VschReader().read(new ByteArrayInputStream(fileWith("PLACE", -1, false))));
        assertThrows(IOException.class, () -> new VschReader().read(new ByteArrayInputStream(fileWith("PLACE", 0, true))));
        byte[] truncated = fileWith("PLACE", 0, false);
        assertThrows(IOException.class, () -> new VschReader().read(
                new ByteArrayInputStream(truncated, 0, truncated.length - 2)));
    }

    @Test
    void failedWriteKeepsPreviousFileAndRemovesTemporaryFile() throws IOException {
        Path target = directory.resolve("build.vsch");
        Files.writeString(target, "previous");
        Scheme scheme = new Scheme(1, 1, 1, Map.of(0, "x".repeat(Instruction.MAX_STATE_LENGTH + 1)), new int[] {0});
        assertThrows(IOException.class, () -> new VschWriter().write(scheme, target));
        assertEquals("previous", Files.readString(target));
        try (var files = Files.list(directory)) {
            assertEquals(List.of(target), files.toList());
        }
    }

    @Test
    void storeChoosesNewestAndRemembersLastSuccessfulWrite() throws IOException {
        Path old = directory.resolve("old.vsch");
        Path newer = directory.resolve("new.vsch");
        Files.writeString(old, "old");
        Files.writeString(newer, "new");
        Files.setLastModifiedTime(old, java.nio.file.attribute.FileTime.fromMillis(1000));
        Files.setLastModifiedTime(newer, java.nio.file.attribute.FileTime.fromMillis(2000));
        VschStore store = new VschStore(directory);
        assertEquals(newer, store.latest().orElseThrow());
        store.record(old);
        assertEquals(old, store.latest().orElseThrow());
        Files.delete(old);
        assertEquals(newer, store.latest().orElseThrow());
        assertTrue(store.destinationFor(Path.of("house.schem")).endsWith("house.vsch"));
        assertFalse(store.latest().isEmpty());
    }

    private byte[] fileWith(String action, int delay, boolean extra) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        DataOutputStream header = new DataOutputStream(output);
        header.writeInt(VschWriter.MAGIC);
        try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(header))) {
            data.writeInt(1);
            data.writeUTF(action);
            data.writeInt(0);
            data.writeInt(0);
            data.writeInt(0);
            data.writeUTF("minecraft:stone");
            data.writeInt(delay);
            if (extra) {
                data.writeByte(1);
            }
        }
        return output.toByteArray();
    }
}
