package ru.autoschem.watch;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import ru.autoschem.scheme.ResourceCalculator;
import ru.autoschem.scheme.SchemReader;
import ru.autoschem.scheme.Scheme;
import ru.autoschem.vsch.VschStore;
import ru.autoschem.vsch.VschWriter;

public final class SchemeWatcher implements AutoCloseable {
    private static final long SETTLE_MILLIS = 800;
    private static final int MAX_ATTEMPTS = 3;

    private final Path schemesDirectory;
    private final VschStore store;
    private final Consumer<Result> onSuccess;
    private final BiConsumer<Path, Throwable> onError;
    private final SchemReader reader = new SchemReader();
    private final ResourceCalculator calculator = new ResourceCalculator();
    private final VschWriter writer = new VschWriter();
    private final Map<Path, Pending> pending = new LinkedHashMap<>();
    private volatile boolean closed;
    private WatchService service;
    private Thread thread;

    public SchemeWatcher(Path schemesDirectory, VschStore store, Consumer<Result> onSuccess,
            BiConsumer<Path, Throwable> onError) {
        this.schemesDirectory = schemesDirectory;
        this.store = store;
        this.onSuccess = onSuccess;
        this.onError = onError;
    }

    public synchronized void start() throws IOException {
        if (service != null || closed) {
            throw new IllegalStateException("Наблюдатель уже запущен или остановлен");
        }
        Files.createDirectories(schemesDirectory);
        WatchService newService = schemesDirectory.getFileSystem().newWatchService();
        try {
            schemesDirectory.register(newService, StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            service = newService;
            scan();
        } catch (IOException | RuntimeException exception) {
            newService.close();
            service = null;
            throw exception;
        }
        thread = new Thread(this::run, "AutoSchem-SchemeWatcher");
        thread.setDaemon(true);
        thread.start();
    }

    private void run() {
        try {
            while (!closed) {
                WatchKey key = service.poll(250, TimeUnit.MILLISECONDS);
                if (key != null) {
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            scan();
                        } else if (event.context() instanceof Path relative) {
                            schedule(schemesDirectory.resolve(relative), 0);
                        }
                    }
                    if (!key.reset()) {
                        throw new IOException("Папка схем больше недоступна");
                    }
                }
                processReady();
            }
        } catch (ClosedWatchServiceException ignored) {
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (!closed) {
                onError.accept(schemesDirectory, exception);
            }
        } catch (Exception exception) {
            if (!closed) {
                onError.accept(schemesDirectory, exception);
            }
        }
    }

    private void scan() throws IOException {
        try (Stream<Path> files = Files.list(schemesDirectory)) {
            List<Path> existing = files.filter(this::isScheme).sorted(Comparator.comparingLong(this::modifiedTime)
                    .thenComparing(Path::toString)).toList();
            for (Path path : existing) {
                schedule(path, 0);
            }
        }
    }

    private long modifiedTime(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis();
        } catch (IOException exception) {
            return Long.MIN_VALUE;
        }
    }

    private boolean isScheme(Path path) {
        return path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".schem")
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private void schedule(Path path, int attempts) {
        if (isScheme(path)) {
            pending.put(path, new Pending(System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SETTLE_MILLIS), attempts));
        }
    }

    private void processReady() {
        long now = System.nanoTime();
        List<Path> ready = new ArrayList<>();
        for (Map.Entry<Path, Pending> entry : pending.entrySet()) {
            if (now >= entry.getValue().deadline()) {
                ready.add(entry.getKey());
            }
        }
        for (Path path : ready) {
            Pending item = pending.remove(path);
            if (item == null || !isScheme(path) || closed) {
                continue;
            }
            try {
                BasicFileAttributes before = attributes(path);
                Scheme scheme = reader.read(path);
                if (!unchanged(before, attributes(path))) {
                    schedule(path, 0);
                    continue;
                }
                Map<String, Integer> resources = calculator.calculate(scheme);
                Path output = store.destinationFor(path);
                writer.write(scheme, output);
                if (!unchanged(before, attributes(path))) {
                    schedule(path, 0);
                    continue;
                }
                store.record(output);
                onSuccess.accept(new Result(path, output, resources));
            } catch (Exception exception) {
                if (!isScheme(path)) {
                    continue;
                }
                if (item.attempts() + 1 < MAX_ATTEMPTS) {
                    schedule(path, item.attempts() + 1);
                } else {
                    onError.accept(path, new IOException("Не удалось обработать схему " + path.getFileName(), exception));
                }
            }
        }
    }

    private BasicFileAttributes attributes(Path path) throws IOException {
        return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    }

    private boolean unchanged(BasicFileAttributes first, BasicFileAttributes second) {
        return first.size() == second.size()
                && first.lastModifiedTime().equals(second.lastModifiedTime())
                && Objects.equals(first.fileKey(), second.fileKey());
    }

    @Override
    public synchronized void close() throws IOException {
        closed = true;
        if (service != null) {
            service.close();
        }
        if (thread != null) {
            thread.interrupt();
        }
    }

    private record Pending(long deadline, int attempts) {
    }

    public record Result(Path scheme, Path vsch, Map<String, Integer> resources) {
    }
}
