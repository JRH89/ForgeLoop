package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RunJournalSessionTest {
    @TempDir Path temporary;

    @Test void disabledRunRecordDoesNotCreateALocalJournalFile() throws Exception {
        Path state = temporary.resolve("state");
        RunnerTask task = task(false);

        assertNull(RunJournalSession.start(task, new RunnerLease("lease-1", "nonce"),
                state.resolve("lease.json"), null, null, null));
        assertFalse(Files.exists(state.resolve("journals")));
    }

    @Test void optedInSessionPinsContextAndHashableCommitEvidence() throws Exception {
        Path repository = temporary.resolve("repository");
        Files.createDirectories(repository);
        run(repository, "git", "init");
        run(repository, "git", "config", "user.email", "runner@example.test");
        run(repository, "git", "config", "user.name", "ForgeLoop Runner");
        Files.writeString(repository.resolve("README.md"), "base\n");
        run(repository, "git", "add", ".");
        run(repository, "git", "commit", "-m", "base");
        Path policyFile = temporary.resolve("provider-policy.json");
        Files.writeString(policyFile, "{\"version\":1}");
        Path state = temporary.resolve("state");
        RunJournalSession session = RunJournalSession.start(task(true), new RunnerLease("lease-1", "nonce"),
                state.resolve("lease.json"), repository, null, policyFile);

        assertNotNull(session);
        session.context("REPOSITORY", "pinned repository context", null, false);
        Files.writeString(repository.resolve("README.md"), "changed\n");
        GitWorktreeManager git = new GitWorktreeManager();
        String commitSha = git.commit(repository, "feat: test commit evidence");
        GitWorktreeManager.CommitEvidence commit = git.commitEvidence(repository, commitSha);
        session.commit(commit);
        session.workerEnded("COMPLETED", commitSha);

        List<JsonNode> records = session.journal().records();
        assertEquals(List.of("WORKER_STARTED", "ATTEMPT_PINS", "CONTEXT_BUILT", "COMMIT_CREATED", "WORKER_ENDED"),
                records.stream().map(record -> record.path("type").asText()).toList());
        JsonNode pins = records.get(1);
        assertTrue(pins.path("providerPolicySha256").asText().matches("[0-9a-f]{64}"));
        assertTrue(pins.path("toolVersions").isObject());
        JsonNode context = records.get(2);
        assertEquals(EvidenceDigests.sha256("pinned repository context".getBytes(StandardCharsets.UTF_8)),
                context.path("sha256").asText());
        JsonNode recordedCommit = records.get(3);
        assertEquals(commitSha, hashRawCommit(recordedCommit.path("rawCommit").asText()));
        assertEquals(commit.fileSha256(), new com.fasterxml.jackson.databind.ObjectMapper()
                .convertValue(recordedCommit.path("fileSha256"), new com.fasterxml.jackson.core.type.TypeReference<>() { }));
    }

    private static RunnerTask task(boolean runRecord) {
        return new RunnerTask("task-1", "IMPLEMENTATION", "Task", "owner/repo", "main", "issue-1",
                "Implement a small change", "git", runRecord);
    }

    private static String hashRawCommit(String rawCommit) throws Exception {
        Process process = new ProcessBuilder("git", "hash-object", "-t", "commit", "--stdin").start();
        try (var input = process.getOutputStream()) { input.write(rawCommit.getBytes(StandardCharsets.UTF_8)); }
        String hash = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (process.waitFor() != 0) throw new IllegalStateException("Git could not hash the recorded commit object");
        return hash;
    }

    private static void run(Path directory, String... command) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("Test Git command failed: " + output);
    }
}
