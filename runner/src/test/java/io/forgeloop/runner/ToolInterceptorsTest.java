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
    void enforcesRepositoryProtectedGlobsAndAllowsOnlyTheExplicitWorkflowOptOut() throws Exception {
        Files.createDirectories(worktree.resolve("src/private"));
        EnforcementDescriptor custom = EnforcementDescriptor.fromDispatched("ANY", List.of(), null,
                List.of("src/private/**"), false, null, List.of());
        ToolOutcome customDenied = invoke(ToolRegistry.standard(new GitWorktreeManager(), null), custom,
                call("write_file", "{\"path\":\"src/private/Token.java\",\"content\":\"class Token {}\"}"));
        assertEquals(FailureCategory.PERMISSION, customDenied.category());
        assertFalse(Files.exists(worktree.resolve("src/private/Token.java")));

        EnforcementDescriptor workflowAllowed = EnforcementDescriptor.fromDispatched("ANY", List.of(), null,
                List.of("src/private/**"), true, null, List.of());
        ToolOutcome workflow = invoke(ToolRegistry.standard(new GitWorktreeManager(), null), workflowAllowed,
                call("write_file", "{\"path\":\".github/workflows/release.yml\",\"content\":\"name: release\"}"));
        ToolOutcome customStillDenied = invoke(ToolRegistry.standard(new GitWorktreeManager(), null), workflowAllowed,
                call("write_file", "{\"path\":\"src/private/StillProtected.java\",\"content\":\"class Protected {}\"}"));

        assertEquals(ToolStatus.OK, workflow.status(), workflow.content() + " " + workflow.meta());
        assertTrue(Files.exists(worktree.resolve(".github/workflows/release.yml")));
        assertEquals(FailureCategory.PERMISSION, customStillDenied.category());
        assertFalse(Files.exists(worktree.resolve("src/private/StillProtected.java")));
    }

    @Test
    void redirectsFinishUntilTheConfiguredGatePassesAfterTheLastWrite() throws Exception {
        EnforcementDescriptor descriptor = EnforcementDescriptor.fromDispatched("NO_TESTS", List.of("**/*Test.java"),
                null, List.of(), false, "verify", List.of("verify"));
        Files.writeString(worktree.resolve("src/Feature.java"), "class Feature {}\n");
        ToolRegistry registry = ToolRegistry.standard(new GitWorktreeManager(), null);

        ToolOutcome stale = invoke(registry, descriptor, call("finish", "{\"summary\":\"finish\"}"),
                context(Map.of("verify", gateOutcome(0, false, 2)), 3));
        ToolOutcome reconstructed = invoke(registry, descriptor, call("finish", "{\"summary\":\"finish\"}"),
                context(Map.of("verify", gateOutcome(0, false, 2)), 3));
        assertEquals(FailureCategory.BUSINESS_RULE, stale.category());
        assertEquals("run_gate", stale.meta().get("redirectTool"));
        assertTrue(stale.content().contains("finish needs a run of gate verify after your last change that passes."));
        assertEquals(stale.meta().get("decision"), reconstructed.meta().get("decision"));
        assertEquals(stale.meta().get("redirectTool"), reconstructed.meta().get("redirectTool"));
        assertEquals(stale.content(), reconstructed.content());
        assertFalse(command("git", "-C", worktree.toString(), "status", "--porcelain").isBlank());

        ToolOutcome failed = invoke(registry, descriptor, call("finish", "{\"summary\":\"finish\"}"),
                context(Map.of("verify", gateOutcome(1, false, 4)), 3));
        assertEquals("run_gate", failed.meta().get("redirectTool"));

        ToolOutcome timedOut = invoke(registry, descriptor, call("finish", "{\"summary\":\"finish\"}"),
                context(Map.of("verify", gateOutcome(0, true, 4)), 3));
        assertEquals("run_gate", timedOut.meta().get("redirectTool"));

        ToolOutcome passed = invoke(registry, descriptor, call("finish", "{\"summary\":\"finish\"}"),
                context(Map.of("verify", gateOutcome(0, false, 4)), 3));
        assertEquals(ToolStatus.OK, passed.status());
        assertTrue(command("git", "-C", worktree.toString(), "status", "--porcelain").isBlank());
    }

    @Test
    void testsOnlyFinishGateRequiresACompletedRunButNotZeroExitCode() throws Exception {
        EnforcementDescriptor descriptor = EnforcementDescriptor.fromDispatched("TESTS_ONLY", List.of("src/**"),
                null, List.of(), false, "tests", List.of("tests"));
        Files.writeString(worktree.resolve("src/FeatureTest.java"), "class FeatureTest {}\n");
        ToolRegistry registry = ToolRegistry.standard(new GitWorktreeManager(), null);

        ToolOutcome stale = invoke(registry, descriptor, call("finish", "{\"summary\":\"finish\"}"),
                context(Map.of("tests", gateOutcome(1, false, 3)), 3));
        assertEquals("run_gate", stale.meta().get("redirectTool"));
        assertTrue(stale.content().contains("after your last change."));

        ToolOutcome completed = invoke(registry, descriptor, call("finish", "{\"summary\":\"finish\"}"),
                context(Map.of("tests", gateOutcome(1, false, 4)), 3));
        assertEquals(ToolStatus.OK, completed.status());
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
        return invoke(registry, descriptor, call, context(Map.of(), 0));
    }

    private ToolOutcome invoke(ToolRegistry registry, EnforcementDescriptor descriptor, ToolCall call,
                               ToolContext context) throws Exception {
        StepJournal journal = new StepJournal(temporaryDirectory.resolve("state"), "task", "lease", Clock.systemUTC());
        ToolGateway gateway = new ToolGateway(registry, journal, List.of())
                .withInterceptors(ToolInterceptors.forDescriptor(descriptor));
        return gateway.invoke(call, context);
    }

    private ToolContext context(Map<String, ToolOutcome> gateOutcomes, int lastWriteStep) {
        Set<String> grants = ToolGrants.forRole("IMPLEMENTATION", true);
        return new ToolContext("task", "lease", "IMPLEMENTATION", worktree, grants,
                List.of("src/", ".github/"), List.of(), 1, 1, 60_000,
                new LoopCounters(0, 0, 0, 0, 0, 0), gateOutcomes, null, "a".repeat(40), lastWriteStep);
    }

    private static ToolOutcome gateOutcome(int exitCode, boolean timedOut, int step) {
        return ToolOutcome.ok("gate result", Map.of("gate", "verify", "exitCode", exitCode,
                "timedOut", timedOut, "step", step), List.of());
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
