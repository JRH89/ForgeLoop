package io.forgeloop.runner;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runner-local durable spend plus outstanding reservations. Never stores request or credential data.
 * Requires POSIX permissions and atomic replacement/directory fsync; unsupported filesystems fail closed.
 */
final class ProviderSpendLedger {
    private static final ConcurrentHashMap<Path, Object> LOCKS = new ConcurrentHashMap<>();
    private static final String OWNER_ONLY = "rw-------";
    private final Path ledger;
    private final long limit;

    ProviderSpendLedger(Path ledger, long limit) {
        if (!ledger.isAbsolute() || limit < 1) throw new IllegalArgumentException("Spend ledger configuration is invalid");
        this.ledger = ledger.normalize(); this.limit = limit;
    }

    void reserve(long amount) throws ProviderException { update(amount, 0, true); }
    void settle(long reserved, long actual) throws ProviderException { update(actual, reserved, false); }

    private void update(long added, long removed, boolean reserve) throws ProviderException {
        synchronized (LOCKS.computeIfAbsent(ledger, ignored -> new Object())) {
            try {
                Path parent = ledger.getParent();
                if (!Files.isDirectory(parent)) throw new IOException("Ledger parent must exist");
                Path lockPath = ledger.resolveSibling(ledger.getFileName() + ".lock");
                createOwnerOnly(lockPath);
                checkOwnerOnly(lockPath);
                try (FileChannel lockChannel = FileChannel.open(lockPath, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS);
                     var ignored = lockChannel.lock()) {
                    long total = 0;
                    if (Files.exists(ledger, LinkOption.NOFOLLOW_LINKS)) {
                        checkOwnerOnly(ledger);
                        if (Files.size(ledger) > 32) throw new IOException("Ledger is invalid");
                        String text = Files.readString(ledger, StandardCharsets.US_ASCII);
                        if (!text.matches("[0-9]+\\n")) throw new IOException("Ledger is invalid");
                        total = Long.parseLong(text.trim());
                    }
                    if (added < 0 || removed < 0 || removed > total) throw new IOException("Ledger accounting is invalid");
                    long updated = Math.addExact(Math.subtractExact(total, removed), added);
                    if (reserve && updated > limit) throw new ProviderException("Runner provider spend cap exhausted", false,
                            null, null, "BUDGET_EXHAUSTED", null);
                    // A separate lock inode allows atomic replacement without letting another process bypass the lock.
                    Path temporary = Files.createTempFile(parent, ".provider-spend-", ".tmp", PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY)));
                    try {
                        try (FileChannel output = FileChannel.open(temporary, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS)) {
                            ByteBuffer bytes = StandardCharsets.US_ASCII.encode(Long.toString(updated) + "\n");
                            while (bytes.hasRemaining()) output.write(bytes);
                            output.force(true);
                        }
                        Files.move(temporary, ledger, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                        try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) { directory.force(true); }
                    } finally { Files.deleteIfExists(temporary); }
                }
            } catch (ProviderException failure) { throw failure;
            } catch (Exception failure) {
                throw new ProviderException("Runner provider spend ledger is unavailable or invalid", false, null, null, "CONFIGURATION_ERROR", null);
            }
        }
    }

    private static void createOwnerOnly(Path path) throws IOException {
        try { Files.createFile(path, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY))); }
        catch (FileAlreadyExistsException exists) { /* Existing lock must pass the same strict checks. */ }
    }
    private static void checkOwnerOnly(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString(OWNER_ONLY)))
            throw new IOException("Ledger files must be owner-only regular files");
    }
}
