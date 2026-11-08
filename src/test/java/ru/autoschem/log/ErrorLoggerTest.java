package ru.autoschem.log;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.util.math.BlockPos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.autoschem.client.ChatMessages;

class ErrorLoggerTest {
    @TempDir
    Path directory;

    @Test
    void writesDateCoordinatesAndStackTraceAndAppendsLaterErrors() throws Exception {
        ErrorLogger logger = new ErrorLogger(directory, new ChatMessages());
        logger.updatePosition(new BlockPos(12, 34, -5));
        logger.report(new IllegalStateException("first error"), new BlockPos(-7, 10, 9));
        logger.updatePosition(null);
        logger.report(new RuntimeException("second error"));
        String report = Files.readString(directory.resolve("crash_report.txt"));
        assertTrue(report.contains("Дата: "));
        assertTrue(report.contains("Координаты игрока: 12, 34, -5"));
        assertTrue(report.contains("Координаты блока: -7, 10, 9"));
        assertTrue(report.contains("IllegalStateException: first error"));
        assertTrue(report.contains("Координаты игрока: неизвестны"));
        assertTrue(report.contains("RuntimeException: second error"));
    }
}
