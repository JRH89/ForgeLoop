package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ToolInterceptorsTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporaryDirectory;
    private Path worktree;

    @BeforeEach
    void createRepository() throws Exception {
        worktree = temporaryDirectory.resolve("repo");
        Files.createDirectories(worktree.resolve("src"));
        Files.createDirectories(worktree.resolve(".github/workflows"));
        Files.writeString(worktree.resolve(".env"), "LOCAL_SECRET=hidden\n");
        Files.writeString(worktree.resolve(".env.example"), "LOCAL_SECRET=replace-me\n");
        Files.writeString(worktree.resolve(".github/workflows/ci.yml"), "name: baseline\n");
        Files.writeString(worktree.resolve("src/Config.java"), "class Config {}\n");
        command("git", "init", worktree.toString());
        command("git", "-C", worktree.toString(), "config", "user.name", "ForgeLoop Test");
        command("git", "-C", worktree.toString(), "config", "user.email", "runner@example.test");
        command("git", "-C", worktree.toString(), "add", "--all");
        command("git", "-C", worktree.toString(), "commit", "-m", "base");
    }

    @Test
    void blocksCredentialReadsAndAllowsExplicitExamples() throws Exception {
        ToolOutcome secret = invoke(ToolRegistry.standard(new GitWorktreeManager(), null),
                EnforcementDescriptor.defaults(List.of()), call("read_file", "{\"path\":\"./.env\",\"startLine\":1,\"maxLines\":10}"));
        assertEquals(FailureCategory.PERMISSION, secret.category());
        assertEquals("credential-files", secret.meta().get("interceptor"));

        ToolOutcome example = invoke(ToolRegistry.standard(new GitWorktreeManager(), null),
                EnforcementDescriptor.defaults(List.of()), call("read_file", "{\"path\":\".env.example\",\"startLine\":1,\"maxLines\":10}"));
        assertEquals(ToolStatus.OK, example.status());
        assertTrue(example.content().contains("replace-me"));
    }

    @Test
    void refusesSecretAndProtectedWritesBeforeFilesystemMutation() throws Exception {
        String cleanStatus = command("git", "-C", worktree.toString(), "status", "--porcelain=v1", "--untracked-files=all", "--ignored");
        ToolOutcome token = invoke(ToolRegistry.standard(new GitWorktreeManager(), null),
                EnforcementDescriptor.defaults(List.of()), call("write_file", "{\"path\":\"src/Leak.java\",\"content\":\"ghp_1234567890123456789012345678901234\"}"));
        ToolOutcome workflow = invoke(ToolRegistry.standard(new GitWorktreeManager(), null),
                EnforcementDescriptor.defaults(List.of()), call("write_file", "{\"path\":\".GitHub/Workflows/ci.yml\",\"content\":\"name: injected\"}"));

        assertEquals(FailureCategory.PERMISSION, token.category());
        assertEquals(FailureCategory.PERMISSION, workflow.category());
        assertFalse(Files.exists(worktree.resolve("src/Leak.java")));
        assertEquals("name: baseline\n", Files.readString(worktree.resolve(".github/workflows/ci.yml")));
        assertEquals(cleanStatus, command("git", "-C", worktree.toString(), "status", "--porcelain=v1", "--untracked-files=all", "--ignored"));
    }

    @Test
    void testFirstWriteBoundaryAndFinishCheckHoldWithoutCommitting() throws Exception {
        EnforcementDescriptor noTests = EnforcementDescriptor.fromDispatched("NO_TESTS", List.of("**/*Test.java"),
                null, List.of(), false, null, List.of());
        ToolOutcome denied = invoke(ToolRegistry.standard(new GitWorktreeManager(), null), noTests,
                call("write_file", "{\"path\":\"src/FeatureTest.java\",\"content\":\"class FeatureTest {}\"}"));
        assertEquals(FailureCategory.PERMISSION, denied.category());
        assertFalse(Files.exists(worktree.resolve("src/FeatureTest.java")));

        String base = command("git", "-C", worktree.toString(), "rev-parse", "HEAD");
        Files.writeString(worktree.resolve("src/UnexpectedTest.java"), "class UnexpectedTest {}\n");
        ToolOutcome held = invoke(ToolRegistry.standard(new GitWorktreeManager(), null), noTests,
                call("finish", "{\"summary\":\"finish\"}"));
        assertEquals("HOLD", held.meta().get("decision"));
        assertEquals(HoldClass.BOUNDARY_BREACHED.name(), held.meta().get("holdClass"));
        assertEquals(base, command("git", "-C", worktree.toString(), "rev-parse", "HEAD"));
    }

    @Test
    void redactsProviderBoundResultsAndOmitsCredentialSearchLines() throws Exception {
        ToolRegistry readTool = new ToolRegistry(List.of(new StaticResultTool("read_file",
                "gho_1234567890123456789012345678901234")));
        ToolOutcome redacted = invoke(readTool, EnforcementDescriptor.defaults(List.of()),
                call("read_file", "{\"path\":\"src/Config.java\",\"startLine\":1,\"maxLines\":10}"));
        assertEquals("[REDACTED]", redacted.content());
        assertEquals(1, redacted.meta().get("redactions"));

        ToolRegistry searchTool = new ToolRegistry(List.of(new StaticResultTool("search_files",
                "src/Config.java:1: safe\n.env:1: hidden\n[N matches capped]")));
        ToolOutcome filtered = invoke(searchTool, EnforcementDescriptor.defaults(List.of()),
                call("search_files", "{\"path\":\".\",\"pattern\":\".*\",\"glob\":\"\"}"));
        assertFalse(filtered.content().contains(".env:1"));
        assertTrue(filtered.content().contains("1 matches in credential files omitted"));
        assertEquals(1, filtered.meta().get("credentialFilesOmitted"));
    }

    private ToolOutcome invoke(ToolRegistry registry, EnforcementDescriptor descriptor, ToolCall call) throws Exception {
        StepJournal journal = new StepJournal(temporaryDirectory.resolve("state"), "task", "lease", Clock.systemUTC());
        ToolGateway gateway = new ToolGateway(registry, journal, List.of())
                .withInterceptors(ToolInterceptors.forDescriptor(descriptor));
        Set<String> grants = ToolGrants.forRole("IMPLEMENTATION", false);
        ToolContext context = new ToolContext("task", "lease", "IMPLEMENTATION", worktree, grants,
                List.of("src/", ".github/"), List.of(), 1, 1, 60_000,
                new LoopCounters(0, 0, 0, 0, 0, 0), Map.of(), null, "a".repeat(40), 0);
        return gateway.invoke(call, context);
    }

    private static ToolCall call(String name, String args) throws IOException { return new ToolCall("call-" + name, name, JSON.readTree(args)); }

    private static String command(String... args) throws Exception {
        Process process = new ProcessBuilder(args).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (process.waitFor() != 0) throw new IllegalStateException(new String(output));
        return new String(output).strip();
    }

    private static final class StaticResultTool implements LoopTool {
        private final ToolSpec spec;
        private final String content;
        private StaticResultTool(String name, String content) {
            this.spec = ToolSchemas.spec(name, "test", "{\"type\":\"object\",\"properties\":{},\"additionalProperties\":true}");
            this.content = content;
        }
        @Override public ToolSpec spec() { return spec; }
        @Override public void validateArguments(JsonNode args) { }
        @Override public ToolOutcome execute(ToolCall call, ToolContext context) { return ToolOutcome.ok(content); }
    }
}
