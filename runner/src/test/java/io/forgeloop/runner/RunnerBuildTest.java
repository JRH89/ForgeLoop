package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunnerBuildTest {
    @TempDir Path temporaryDirectory;

    @Test void currentBuildHasAnExplicitRevisionAndClasspathOrJarIdentity() {
        RunnerBuild build = RunnerBuild.current();
        assertTrue("unknown".equals(build.revision()) || build.revision().matches("[0-9a-f]{7,64}"));
        assertTrue("unpackaged".equals(build.jarSha256()) || build.jarSha256().matches("[0-9a-f]{64}"));
    }

    @Test void buildIdentityRejectsMalformedPins() {
        assertDoesNotThrow(() -> new RunnerBuild("unknown", "unpackaged"));
        assertEquals("a".repeat(64), new RunnerBuild("0123456", "a".repeat(64)).jarSha256());
        assertThrows(IllegalArgumentException.class, () -> new RunnerBuild("main", "unpackaged"));
        assertThrows(IllegalArgumentException.class, () -> new RunnerBuild("unknown", "not-a-digest"));
    }

    @Test void readsTheFilteredBuildRevision() throws Exception {
        String revision = RunnerBuild.readRevision(new java.io.ByteArrayInputStream(
                "revision=deadbeef0\n".getBytes(StandardCharsets.ISO_8859_1)));

        assertEquals("deadbeef0", revision);
    }

    @Test void classDirectoriesAreExplicitlyUnpackaged() {
        assertEquals(new RunnerBuild("deadbeef0", "unpackaged"),
                RunnerBuild.fromCodeSource("deadbeef0", temporaryDirectory));
    }

    @Test void deployedJarIdentityIsItsContentSha256() throws Exception {
        byte[] bytes = "runner jar fixture".getBytes(StandardCharsets.UTF_8);
        Path jar = temporaryDirectory.resolve("runner.jar");
        Files.write(jar, bytes);
        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));

        assertEquals(new RunnerBuild("deadbeef0", expected), RunnerBuild.fromCodeSource("deadbeef0", jar));
    }
}
