package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScreenshotEvidenceCollectorTest {
    @TempDir Path evidence;

    @Test
    void collectsOnlyValidPngFilesFromBoundedPlaywrightDirectories() throws Exception {
        Files.createDirectories(evidence.resolve("test-results/scenario"));
        Files.createDirectories(evidence.resolve("playwright-report/data"));
        Files.write(evidence.resolve("test-results/scenario/after.png"), png());
        Files.writeString(evidence.resolve("test-results/scenario/not-a-png.png"), "not an image");
        Files.write(evidence.resolve("playwright-report/data/before.png"), png());
        Files.write(evidence.resolve("unrelated.png"), png());

        var screenshots = new ScreenshotEvidenceCollector().collect(evidence);

        assertEquals(2, screenshots.size());
        assertEquals("screenshot-01-before.png", screenshots.get(0).displayName());
        assertEquals("screenshot-02-after.png", screenshots.get(1).displayName());
    }

    private static byte[] png() {
        return new byte[]{(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 0, 0, 0, 0};
    }
}
