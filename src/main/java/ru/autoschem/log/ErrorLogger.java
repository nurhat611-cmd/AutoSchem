package ru.autoschem.log;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.autoschem.client.ChatMessages;

public final class ErrorLogger {
    private static final Logger LOGGER = LoggerFactory.getLogger("AutoSchem");

    private final Path report;
    private final ChatMessages messages;
    private final AtomicReference<BlockPos> playerPosition = new AtomicReference<>();

    public ErrorLogger(Path configDirectory, ChatMessages messages) {
        this.report = configDirectory.resolve("crash_report.txt");
        this.messages = messages;
    }

    public void updatePosition(BlockPos position) {
        playerPosition.set(position == null ? null : position.toImmutable());
    }

    public void report(Throwable error) {
        report(error, null);
    }

    public synchronized void report(Throwable error, BlockPos target) {
        LOGGER.error("AutoSchem error", error);
        StringWriter stack = new StringWriter();
        error.printStackTrace(new PrintWriter(stack));
        String text = "Дата: " + ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                + System.lineSeparator() + "Координаты игрока: " + format(playerPosition.get())
                + System.lineSeparator() + "Координаты блока: " + format(target)
                + System.lineSeparator() + stack + System.lineSeparator();
        try {
            Files.createDirectories(report.getParent());
            Files.writeString(report, text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException writeFailure) {
            LOGGER.error("Cannot write AutoSchem crash report", writeFailure);
        }
        messages.enqueue("Ошибка! Лог в crash_report.txt.", Formatting.RED);
    }

    private String format(BlockPos position) {
        return position == null ? "неизвестны"
                : position.getX() + ", " + position.getY() + ", " + position.getZ();
    }
}
