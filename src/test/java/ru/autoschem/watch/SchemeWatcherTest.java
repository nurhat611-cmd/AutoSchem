package ru.autoschem.watch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.autoschem.vsch.Instruction;
import ru.autoschem.vsch.VschReader;
import ru.autoschem.vsch.VschStore;

class SchemeWatcherTest {
    @TempDir
    Path directory;

    @Test
    void automaticallyProcessesNewFileAndPublishesCompleteVsch() throws Exception {
        Path schemes = directory.resolve("schemes");
        VschStore store = new VschStore(directory.resolve("vsch"));
        CountDownLatch processed = new CountDownLatch(1);
        List<SchemeWatcher.Result> results = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        try (SchemeWatcher watcher = new SchemeWatcher(schemes, store, result -> {
            results.add(result);
            processed.countDown();
        }, (path, error) -> errors.add(error))) {
            watcher.start();
            writeScheme(schemes.resolve("house.schem"), "minecraft:stone");
            assertTrue(processed.await(10, TimeUnit.SECONDS));
            assertTrue(errors.isEmpty());
            assertEquals(Map.of("minecraft:stone", 1), results.getFirst().resources());
            Path output = store.latest().orElseThrow();
            assertEquals("house.vsch", output.getFileName().toString());
            assertEquals(List.of(new Instruction(Instruction.Action.PLACE, 0, 0, 0, "minecraft:stone", 1)),
                    new VschReader().read(output));
        }
    }

    @Test
    void startupScanKeepsMostRecentlyModifiedSchemeLast() throws Exception {
        Path schemes = directory.resolve("schemes");
        Files.createDirectories(schemes);
        Path older = schemes.resolve("older.schem");
        Path newer = schemes.resolve("newer.schem");
        writeScheme(newer, "minecraft:dirt");
        writeScheme(older, "minecraft:stone");
        Files.setLastModifiedTime(older, FileTime.fromMillis(1000));
        Files.setLastModifiedTime(newer, FileTime.fromMillis(2000));
        VschStore store = new VschStore(directory.resolve("vsch"));
        CountDownLatch processed = new CountDownLatch(2);
        List<String> names = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        try (SchemeWatcher watcher = new SchemeWatcher(schemes, store, result -> {
            names.add(result.scheme().getFileName().toString());
            processed.countDown();
        }, (path, error) -> errors.add(error))) {
            watcher.start();
            assertTrue(processed.await(10, TimeUnit.SECONDS));
            assertTrue(errors.isEmpty());
            assertEquals(List.of("older.schem", "newer.schem"), names);
            assertEquals("newer.vsch", store.latest().orElseThrow().getFileName().toString());
        }
    }

    @Test
    void retriesIncompleteInputAndReportsPersistentErrors() throws Exception {
        Path schemes = directory.resolve("schemes");
        VschStore store = new VschStore(directory.resolve("vsch"));
        CountDownLatch failed = new CountDownLatch(1);
        List<Throwable> errors = new CopyOnWriteArrayList<>();
        try (SchemeWatcher watcher = new SchemeWatcher(schemes, store, result -> {
        }, (path, error) -> {
            errors.add(error);
            failed.countDown();
        })) {
            watcher.start();
            Files.writeString(schemes.resolve("bad.schem"), "not an nbt file");
            assertTrue(failed.await(10, TimeUnit.SECONDS));
            assertFalse(errors.isEmpty());
            assertTrue(store.latest().isEmpty());
        }
    }

    private void writeScheme(Path path, String state) throws IOException {
        NbtCompound root = new NbtCompound();
        root.putInt("Version", 2);
        root.putShort("Width", (short) 1);
        root.putShort("Height", (short) 1);
        root.putShort("Length", (short) 1);
        NbtCompound palette = new NbtCompound();
        palette.putInt(state, 0);
        root.put("Palette", palette);
        root.putByteArray("BlockData", new byte[] {0});
        NbtIo.writeCompressed(root, path);
    }
}
