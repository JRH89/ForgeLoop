package io.forgeloop.runner;

import java.io.InputStream;
import java.io.BufferedInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Properties;

/** Immutable identity for the runner build that acknowledges a task lease. */
public record RunnerBuild(String revision, String jarSha256) {
    private static final RunnerBuild CURRENT = loadCurrent();

    public RunnerBuild {
        if (revision == null || !("unknown".equals(revision) || revision.matches("[0-9a-f]{7,64}")))
            throw new IllegalArgumentException("Runner revision is invalid");
        if (jarSha256 == null || !("unpackaged".equals(jarSha256) || jarSha256.matches("[0-9a-f]{64}")))
            throw new IllegalArgumentException("Runner JAR digest is invalid");
    }

    /** Reads the build revision and hashes the deployed JAR, while keeping IDE/test runs identifiable. */
    public static RunnerBuild current() {
        return CURRENT;
    }

    private static RunnerBuild loadCurrent() {
        String revision = readRevision();
        try {
            Path codeSource = Path.of(RunnerMain.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return fromCodeSource(revision, codeSource);
        } catch (Exception failure) {
            throw new IllegalStateException("Runner build identity could not be calculated", failure);
        }
    }

    /** Returns an explicit unpackaged identity for class directories and hashes only deployed JARs. */
    static RunnerBuild fromCodeSource(String revision, Path codeSource) {
        if (codeSource == null || !Files.isRegularFile(codeSource)
                || codeSource.getFileName() == null || !codeSource.getFileName().toString().endsWith(".jar")) {
            return new RunnerBuild(revision, "unpackaged");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new BufferedInputStream(Files.newInputStream(codeSource))) {
                byte[] buffer = new byte[16 * 1024];
                for (int count; (count = input.read(buffer)) != -1; ) digest.update(buffer, 0, count);
            }
            return new RunnerBuild(revision, HexFormat.of().formatHex(digest.digest()));
        } catch (Exception failure) {
            throw new IllegalStateException("Runner build identity could not be calculated", failure);
        }
    }

    private static String readRevision() {
        try (InputStream input = RunnerBuild.class.getResourceAsStream("/forgeloop-build.properties")) {
            if (input == null) return "unknown";
            return readRevision(input);
        } catch (Exception ignored) {
            // Build metadata must never prevent a local runner from starting; unknown is an explicit pin state.
            return "unknown";
        }
    }

    static String readRevision(InputStream input) throws java.io.IOException {
        if (input == null) return "unknown";
        Properties properties = new Properties();
        properties.load(input);
        String revision = properties.getProperty("revision", "unknown").trim();
        return "unknown".equals(revision) || revision.matches("[0-9a-f]{7,64}") ? revision : "unknown";
    }
}
