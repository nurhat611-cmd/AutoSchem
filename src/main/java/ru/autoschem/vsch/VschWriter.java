package ru.autoschem.vsch;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPOutputStream;
import ru.autoschem.scheme.BlockStates;
import ru.autoschem.scheme.Scheme;

public final class VschWriter {
    public static final int MAGIC = 0x56534348;
    public static final int DEFAULT_DELAY = 1;

    public void write(Scheme scheme, Path destination) throws IOException {
        Path directory = destination.toAbsolutePath().getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".autoschem-", ".tmp");
        try {
            try (OutputStream stream = Files.newOutputStream(temporary)) {
                write(scheme, stream);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public void write(Scheme scheme, OutputStream destination) throws IOException {
        DataOutputStream header = new DataOutputStream(new BufferedOutputStream(destination));
        header.writeInt(MAGIC);
        header.flush();
        try (DataOutputStream data = new DataOutputStream(new GZIPOutputStream(header))) {
            int count = 0;
            for (int index = 0; index < scheme.size(); index++) {
                if (!BlockStates.isStructureVoid(scheme.blockState(index))) {
                    count++;
                }
            }
            data.writeInt(count);
            for (int index = 0; index < scheme.size(); index++) {
                String state = scheme.blockState(index);
                if (BlockStates.isStructureVoid(state)) {
                    continue;
                }
                if (state.length() > Instruction.MAX_STATE_LENGTH) {
                    throw new IOException("Состояние блока слишком длинное");
                }
                int x = index % scheme.width();
                int z = index / scheme.width() % scheme.length();
                int y = index / (scheme.width() * scheme.length());
                boolean air = BlockStates.isAir(state);
                data.writeUTF(air ? Instruction.Action.BREAK.name() : Instruction.Action.PLACE.name());
                data.writeInt(x);
                data.writeInt(y);
                data.writeInt(z);
                data.writeUTF(air ? "minecraft:air" : state);
                data.writeInt(DEFAULT_DELAY);
            }
        }
    }
}
