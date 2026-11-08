package ru.autoschem.vsch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

public final class VschStore {
    private final Path directory;
    private final AtomicReference<Path> latest = new AtomicReference<>();

    public VschStore(Path directory) {
        this.directory = directory;
    }

    public Path destinationFor(Path scheme) {
        String fileName = scheme.getFileName().toString();
        return directory.resolve(fileName.substring(0, fileName.length() - 6) + ".vsch");
    }

    public void record(Path path) {
        latest.set(path);
    }

    public Optional<Path> latest() throws IOException {
        Path current = latest.get();
        if (current != null && Files.isRegularFile(current, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.of(current);
        }
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        try (Stream<Path> files = Files.list(directory)) {
            return files.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".vsch"))
                    .max(Comparator.comparingLong(this::modifiedTime).thenComparing(Path::toString));
        }
    }

    private long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }
}
