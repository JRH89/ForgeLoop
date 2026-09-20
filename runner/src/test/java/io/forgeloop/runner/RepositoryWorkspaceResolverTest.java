package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryWorkspaceResolverTest {
    @TempDir Path temporaryDirectory;

    @Test
    void resolvesOnlyTheExpectedPreclonedRepository() throws Exception {
        Path checkout = temporaryDirectory.resolve("JRH89/Ticketly");
        Files.createDirectories(checkout.resolve(".git"));
        assertEquals(checkout.toAbsolutePath().normalize(), new RepositoryWorkspaceResolver().resolve(temporaryDirectory, "JRH89/Ticketly"));
    }

    @Test
    void rejectsTraversalAndMissingCheckouts() {
        RepositoryWorkspaceResolver resolver = new RepositoryWorkspaceResolver();
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(temporaryDirectory, "../outside"));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(temporaryDirectory, "JRH89/Ticketly"));
    }
}
