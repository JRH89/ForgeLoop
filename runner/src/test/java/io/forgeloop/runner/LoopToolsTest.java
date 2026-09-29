package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoopToolsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporaryDirectory;

    @Test
    void listsReadsAndSearchesBoundedUtf8TextWithoutGitOrBinaryContents() throws Exception {
        Path repo = worktree("read-search");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/App.java"), "first\nmatch hello\nlast\n");
        Files.write(repo.resolve("src/binary.dat"), new byte[] {0, 1, 2});
        Files.writeString(repo.resolve(".git/config"), "secret-token");
        ToolContext context = context(repo, List.of("src/"), List.of(), 60_000);

        ToolOutcome listed = execute(new ListFilesTool(), context, "{\"path\":\".\"}");
        assertTrue(listed.content().contains("src/"));
        assertFalse(listed.content().contains(".git"));
        ToolOutcome page = execute(new ReadFileTool(), context, "{\"path\":\"src/App.java\",\"startLine\":1,\"maxLines\":2}");
        assertTrue(page.content().contains("1: first"));
        assertTrue(page.content().contains("2: match hello"));
        assertTrue(page.content().contains("startLine=3"));
        ToolOutcome search = execute(new SearchFilesTool(), context, "{\"pattern\":\"hello\",\"path\":\".\",\"glob\":\"**/*.java\"}");
        assertEquals("src/App.java:2: match hello", search.content());
        assertEquals(FailureCategory.BUSINESS_RULE,
                execute(new ReadFileTool(), context, "{\"path\":\"src/binary.dat\",\"startLine\":1,\"maxLines\":10}").category());
        assertEquals(FailureCategory.PERMISSION,
                execute(new ReadFileTool(), context, "{\"path\":\".git/config\",\"startLine\":1,\"maxLines\":10}").category());
        assertTrue(execute(new SearchFilesTool(), context, "{\"pattern\":\"[\",\"path\":\".\",\"glob\":\"\"}").category() == FailureCategory.VALIDATION);
        assertEquals(FailureCategory.VALIDATION, execute(new SearchFilesTool(), context,
                "{\"pattern\":\"^(a+)+$\",\"path\":\".\",\"glob\":\"\"}").category());
    }

    @Test
    void writesOnlyOwnedPathsAndEditsOnlyUnambiguousExactText() throws Exception {
        Path repo = worktree("write-edit");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/App.java"), "alpha\nneedle\nomega\n");
        ToolContext context = context(repo, List.of("src/"), List.of(), 60_000);

        ToolOutcome edited = execute(new EditFileTool(), context,
                "{\"path\":\"src/App.java\",\"oldText\":\"needle\",\"newText\":\"updated\",\"replaceAll\":false}");
        assertEquals(ToolStatus.OK, edited.status());
        assertEquals(Hashing.sha256(Files.readString(repo.resolve("src/App.java"))), edited.postImages().getFirst().sha256());
        assertEquals(FailureCategory.VALIDATION, execute(new EditFileTool(), context,
                "{\"path\":\"src/App.java\",\"oldText\":\"absent\",\"newText\":\"x\",\"replaceAll\":false}").category());
        Files.writeString(repo.resolve("src/Duplicates.txt"), "x x");
        assertEquals(FailureCategory.VALIDATION, execute(new EditFileTool(), context,
                "{\"path\":\"src/Duplicates.txt\",\"oldText\":\"x\",\"newText\":\"y\",\"replaceAll\":false}").category());
        ToolOutcome outside = execute(new WriteFileTool(), context, "{\"path\":\"README.md\",\"content\":\"not owned\"}");
        assertEquals(FailureCategory.PERMISSION, outside.category());
        String huge = "a".repeat(500_001);
        assertEquals(FailureCategory.BUSINESS_RULE, execute(new WriteFileTool(), context,
                JSON.writeValueAsString(Map.of("path", "src/Huge.txt", "content", huge))).category());
        ToolOutcome written = execute(new WriteFileTool(), context, "{\"path\":\"src/New.txt\",\"content\":\"new\"}");
        assertEquals("new", Files.readString(repo.resolve("src/New.txt")));
        assertTrue(written.postImages().getFirst().sha256().matches("[0-9a-f]{64}"));
    }

    @Test
    void runGateUsesOnlyDeclaredGateAndRemainingWallTimeWithoutCreatingEvidence() throws Exception {
        Path repo = worktree("gate");
        LoopGate gate = new LoopGate("unit", "maven@sha256:" + "a".repeat(64), List.of("mvn", "test"), 120, false);
        AtomicInteger executions = new AtomicInteger();
        java.util.concurrent.atomic.AtomicReference<java.time.Duration> timeout = new java.util.concurrent.atomic.AtomicReference<>();
        RunGateTool tool = new RunGateTool((requested, worktree, bounded) -> {
            executions.incrementAndGet(); timeout.set(bounded);
            return new VerificationResult(1, false, "full diagnostics", Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
        });
        ToolContext context = context(repo, List.of("src/"), List.of(gate), 60_000);
        ToolOutcome result = execute(tool, context, "{\"name\":\"unit\"}");
        assertEquals(ToolStatus.OK, result.status(), "a failed gate is still an advisory result");
        assertEquals(1, result.meta().get("exitCode"));
        assertEquals("full diagnostics", result.content().substring(result.content().indexOf('\n') + 1));
        assertEquals(java.time.Duration.ofSeconds(60), timeout.get());
        assertEquals(1, executions.get());
        assertEquals(FailureCategory.VALIDATION, execute(tool, context, "{\"name\":\"unknown\"}").category());
        assertEquals(FailureCategory.BUSINESS_RULE, execute(tool, context(repo, List.of("src/"), List.of(gate), 29_999), "{\"name\":\"unit\"}").category());
        assertEquals(1, executions.get());
        assertFalse(Files.exists(repo.resolve("evidence")));
    }

    @Test
    void finishRequiresAtLeastOneOwnedPathAndRejectsDeletes() throws Exception {
        Path repo = gitRepository("finish-tool");
        GitWorktreeManager git = new GitWorktreeManager();
        ToolContext context = context(repo, List.of("src/"), List.of(), 60_000);
        FinishTool finish = new FinishTool(git);
        assertEquals(FailureCategory.BUSINESS_RULE, execute(finish, context, "{\"summary\":\"nothing\"}").category());
        Files.writeString(repo.resolve("README.md"), "outside owned\n");
        assertEquals(FailureCategory.BUSINESS_RULE, execute(finish, context, "{\"summary\":\"outside\"}").category());
        Files.writeString(repo.resolve("README.md"), "base\n");
        Files.createDirectories(repo.resolve("src"));
        Files.writeString(repo.resolve("src/Change.java"), "class Change {}\n");
        ToolOutcome finished = execute(finish, context, "{\"summary\":\"owned change\"}");
        assertEquals(ToolStatus.OK, finished.status());
        assertTrue(finished.meta().get("changeSha").toString().matches("[0-9a-f]{40,64}"));
    }

    private Path worktree(String name) throws IOException {
        Path repo = temporaryDirectory.resolve(name); Files.createDirectories(repo); Files.createDirectory(repo.resolve(".git")); return repo;
    }

    private Path gitRepository(String name) throws Exception {
        Path repo = temporaryDirectory.resolve(name); Files.createDirectories(repo);
        run("git", "init", repo.toString()); run("git", "-C", repo.toString(), "config", "user.email", "runner@example.test");
        run("git", "-C", repo.toString(), "config", "user.name", "ForgeLoop Test");
        Files.writeString(repo.resolve("README.md"), "base\n"); run("git", "-C", repo.toString(), "add", "."); run("git", "-C", repo.toString(), "commit", "-m", "base");
        return repo;
    }

    private static ToolContext context(Path repo, List<String> owned, List<LoopGate> gates, long millis) {
        return new ToolContext("task", "lease", "IMPLEMENTATION", repo, ToolGrants.forRole("IMPLEMENTATION", !gates.isEmpty()), owned,
                gates, 1, 1, millis, new LoopCounters(0, 0, 0, 0, 0, 0), Map.of(), null);
    }

    private static ToolOutcome execute(LoopTool tool, ToolContext context, String raw) throws Exception {
        JsonNode arguments = JSON.readTree(raw);
        ToolCall call = new ToolCall("call-" + tool.spec().name(), tool.spec().name(), arguments);
        try {
            tool.validateArguments(arguments);
            return tool.execute(call, context);
        } catch (LoopToolFailure expected) {
            return ToolOutcome.failed(expected.category(), expected.getMessage());
        }
    }

    private static void run(String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes(); if (process.waitFor() != 0) throw new IllegalStateException(new String(output));
    }
}
