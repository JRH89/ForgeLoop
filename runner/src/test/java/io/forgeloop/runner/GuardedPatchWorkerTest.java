package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuardedPatchWorkerTest {
    @TempDir Path repository;

    @Test void providerCanBeReplacedWithoutChangingWorkerLogic() throws Exception {
        initializeRepository();
        ProviderClient openAiCompatible = ignored -> new ProviderResult("{\"summary\":\"change\",\"changes\":[{\"path\":\"src/result.txt\",\"content\":\"done\",\"message\":\"write result\"}]}", 3, 2, "request-1");

        GuardedPatchResult result = new GuardedPatchWorker().execute(new ProviderExecutionPolicy("openai", "model", 2), openAiCompatible,
                "IMPLEMENTATION", "task", "spec", repository, List.of("src/"));

        assertEquals("done", Files.readString(repository.resolve("src/result.txt")));
        assertEquals("openai", result.usage().provider());
        assertFalse(result.commitSha().isBlank());
    }

    @Test void malformedProviderOutputCannotModifyOrAdvanceWork() throws Exception {
        initializeRepository();
        ProviderClient malformed = ignored -> new ProviderResult("not-json", 1, 1, "request-2");

        assertThrows(GuardedPatchFailure.class, () -> new GuardedPatchWorker().execute(new ProviderExecutionPolicy("anthropic", "model", 1), malformed,
                "IMPLEMENTATION", "task", "spec", repository, List.of("src/")));
        assertFalse(Files.exists(repository.resolve("src/result.txt")));
    }

    @Test void nonCodeRoleCannotReceiveRepositoryWriteCapability() throws Exception {
        initializeRepository();
        ProviderClient provider = ignored -> new ProviderResult("{}", 1, 1, "request");
        assertThrows(IllegalArgumentException.class, () -> new GuardedPatchWorker().execute(new ProviderExecutionPolicy("anthropic", "model", 1), provider,
                "PLANNER", "task", "spec", repository, List.of("src/")));
    }

    private void initializeRepository() throws Exception {
        run("git", "init", repository.toString());
        run("git", "-C", repository.toString(), "config", "user.email", "runner@example.test");
        run("git", "-C", repository.toString(), "config", "user.name", "ForgeLoop Runner");
        Files.writeString(repository.resolve("README.md"), "base");
        run("git", "-C", repository.toString(), "add", ".");
        run("git", "-C", repository.toString(), "commit", "-m", "base");
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        if (process.waitFor() != 0) throw new IllegalStateException(new String(process.getInputStream().readAllBytes()));
    }
}
