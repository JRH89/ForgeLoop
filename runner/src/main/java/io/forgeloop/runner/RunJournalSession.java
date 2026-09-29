package io.forgeloop.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** A single opted-in attempt's journal writer and upload boundary. */
final class RunJournalSession {
    private final StepJournal journal;
    private boolean finished;

    private RunJournalSession(StepJournal journal) { this.journal = journal; }

    static RunJournalSession start(RunnerTask task, RunnerLease lease, Path leaseFile,
                                   Path worktree, ProviderExecutionPolicy policy, Path policyFile) throws IOException {
        if (task == null || lease == null || leaseFile == null) throw new IllegalArgumentException("Run journal session identity is incomplete");
        if (!task.runRecord()) return null;
        Path stateRoot = leaseFile.toAbsolutePath().normalize().getParent();
        if (stateRoot == null) throw new IllegalArgumentException("Runner state directory is invalid");
        StepJournal journal = new StepJournal(stateRoot, task.id(), lease.leaseId(), Clock.systemUTC());
        RunJournalSession session = new RunJournalSession(journal);
        Map<String, Object> started = new LinkedHashMap<>();
        started.put("taskId", task.id()); started.put("repository", task.repository());
        started.put("executionRole", task.role()); started.put("provider", policy == null ? "none" : policy.provider());
        started.put("model", policy == null ? "none" : policy.model());
        started.put("maxAttempts", policy == null ? 0 : policy.maxAttempts());
        started.put("adapter", policy == null ? "none" : new ProviderClientFactory().adapterId(policy.provider()));
        started.put("serializerVersion", "1");
        started.put("baseSha", safeHead(worktree));
        journal.append("WORKER_STARTED", started);

        Map<String, Object> pins = new LinkedHashMap<>();
        RunnerBuild build = RunnerBuild.current();
        pins.put("runnerRevision", build.revision()); pins.put("runnerJarSha256", build.jarSha256());
        pins.put("toolVersions", RunnerToolVersions.current().asMap());
        pins.put("coreAutocrlf", safeConfig(worktree));
        pins.put("providerPolicySha256", fileDigest(policyFile));
        pins.put("providerPolicy", policy);
        pins.put("mcpConfigurations", task.mcpConfigurations());
        pins.put("sourceRef", task.sourceRef()); pins.put("baseBranch", task.baseBranch());
        pins.put("executionBaseRef", task.executionBaseRef()); pins.put("dependencyChangeShas", task.dependencyChangeShas());
        journal.append("ATTEMPT_PINS", pins);
        return session;
    }

    StepJournal journal() { return journal; }

    synchronized boolean markFinished() {
        if (finished) return false;
        finished = true;
        return true;
    }

    void context(String kind, String content, String baseSha, boolean includeContent) throws IOException {
        if (journal == null || content == null || content.isBlank()) return;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("kind", kind); fields.put("sha256", sha256(content.getBytes(StandardCharsets.UTF_8)));
        if ("REVIEW_DIFF".equals(kind)) fields.put("baseSha", baseSha);
        if (includeContent) fields.put("text", content);
        journal.append("CONTEXT_BUILT", fields);
    }

    void patchRefused(String category, String message) throws IOException {
        if (journal == null) return;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("category", bounded(category, 80));
        fields.put("message", EvidenceRedactor.redactTokens(bounded(message, 1000)));
        fields.put("verbatim", true);
        journal.append("PATCH_REFUSED", fields);
    }

    void workerEnded(String category, String changeSha) throws IOException {
        if (journal == null) return;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("category", bounded(category, 80)); fields.put("changeSha", changeSha);
        journal.append("WORKER_ENDED", fields);
    }

    void commit(GitWorktreeManager.CommitEvidence commit) throws IOException {
        if (journal == null || commit == null) return;
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("sha", commit.sha()); fields.put("tree", commit.tree()); fields.put("parent", commit.parent());
        fields.put("rawCommit", commit.rawCommit()); fields.put("fileSha256", commit.fileSha256());
        journal.append("COMMIT_CREATED", fields);
    }

    private static String safeHead(Path worktree) {
        if (worktree == null) return "unavailable";
        try { return new GitWorktreeManager().headSha(worktree); } catch (Exception ignored) { return "unavailable"; }
    }

    private static String safeConfig(Path worktree) {
        if (worktree == null) return "unavailable";
        try { return new GitWorktreeManager().coreAutocrlf(worktree); } catch (Exception ignored) { return "unavailable"; }
    }

    private static String fileDigest(Path path) {
        if (path == null) return "unavailable";
        try { return sha256(Files.readAllBytes(path)); } catch (Exception ignored) { return "unavailable"; }
    }

    private static String bounded(String value, int max) {
        if (value == null || value.isBlank()) return "UNKNOWN";
        String normalized = value.replaceAll("[^A-Za-z0-9_.-]", "_");
        return normalized.substring(0, Math.min(normalized.length(), max));
    }

    private static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception unavailable) { throw new IllegalStateException("SHA-256 is unavailable", unavailable); }
    }
}
