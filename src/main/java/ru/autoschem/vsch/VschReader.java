package ru.autoschem.vsch;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import ru.autoschem.scheme.Scheme;

public final class VschReader {
    public List<Instruction> read(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return read(input);
        }
    }

    public List<Instruction> read(InputStream input) throws IOException {
        DataInputStream header = new DataInputStream(new BufferedInputStream(input));
        if (header.readInt() != VschWriter.MAGIC) {
            throw new IOException("Неверное магическое число VSCH");
        }
        try (DataInputStream data = new DataInputStream(new GZIPInputStream(header))) {
            int count = data.readInt();
            if (count < 0 || count > Scheme.MAX_BLOCKS) {
                throw new IOException("Недопустимое количество инструкций: " + count);
            }
            List<Instruction> instructions = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                try {
                    Instruction.Action action = Instruction.Action.valueOf(data.readUTF());
                    int x = data.readInt();
                    int y = data.readInt();
                    int z = data.readInt();
                    String state = data.readUTF();
                    int delay = data.readInt();
                    instructions.add(new Instruction(action, x, y, z, state, delay));
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Некорректная инструкция VSCH №" + i, exception);
                }
            }
            if (data.read() != -1) {
                throw new IOException("Лишние данные после инструкций VSCH");
            }
            return List.copyOf(instructions);
        }
    }
}
