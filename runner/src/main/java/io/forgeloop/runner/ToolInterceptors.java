package io.forgeloop.runner;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Creates the fixed, ordered enforcement chain from dispatched task policy only. */
public final class ToolInterceptors {
    private ToolInterceptors() { }

    public static List<ToolCallInterceptor> forTask(RunnerTask task, List<LoopGate> gates) {
        return forDescriptor(EnforcementDescriptor.of(task, gates), new GitWorktreeManager());
    }

    public static List<ToolCallInterceptor> forDescriptor(EnforcementDescriptor descriptor) {
        return forDescriptor(descriptor, new GitWorktreeManager());
    }

    static List<ToolCallInterceptor> forDescriptor(EnforcementDescriptor descriptor, GitWorktreeManager git) {
        if (descriptor == null || git == null) throw new IllegalArgumentException("Enforcement chain inputs are required");
        List<ToolCallInterceptor> chain = new ArrayList<>();
        chain.add(new CredentialFilesRule());
        chain.add(new ProtectedPathsRule(descriptor, git));
        if (!"ANY".equals(descriptor.writeBoundary())) chain.add(new WriteBoundaryRule(descriptor, git));
        chain.add(new SecretContentRule());
        chain.add(new ResultRedactionRule());
        return List.copyOf(chain);
    }

    private static final class CredentialFilesRule implements ToolCallInterceptor {
        @Override public String name() { return "credential-files"; }

        @Override public Decision before(ToolCall call, ToolContext context) {
            String path = guardedInputPath(call, context);
            if (path != null && List.of("read_file", "search_files", "write_file", "edit_file").contains(call.name())
                    && CredentialPaths.isCredentialFile(path)) {
                return Decision.deny(FailureCategory.PERMISSION,
                        "Path " + path + " is a credential file; no worker may read or change it.");
            }
            return Decision.allow();
        }

        @Override public ToolOutcome after(ToolCall call, ToolOutcome outcome, ToolContext context) {
            if (!"search_files".equals(call.name()) || outcome.content().isEmpty()) return outcome;
            int omitted = 0;
            List<String> visible = new ArrayList<>();
            for (String line : outcome.content().split("\\R", -1)) {
                int firstColon = line.indexOf(':');
                String candidatePath = firstColon < 0 ? "" : line.substring(0, firstColon);
                if (CredentialPaths.isCredentialFile(candidatePath)) omitted++;
                else visible.add(line);
            }
            if (omitted == 0) return outcome;
            visible.add("[" + omitted + " matches in credential files omitted]");
            Map<String, Object> meta = new LinkedHashMap<>(outcome.meta());
            meta.put("credentialFilesOmitted", omitted);
            return new ToolOutcome(outcome.status(), outcome.category(), String.join("\n", visible), meta, outcome.postImages());
        }
    }

    private static final class ProtectedPathsRule implements ToolCallInterceptor {
        private static final String WORKFLOW_GLOB = ".github/workflows/**";
        private final EnforcementDescriptor descriptor;
        private final GitWorktreeManager git;

        private ProtectedPathsRule(EnforcementDescriptor descriptor, GitWorktreeManager git) {
            this.descriptor = descriptor;
            this.git = git;
        }

        @Override public String name() { return "protected-paths"; }

        @Override public ToolOutcome after(ToolCall call, ToolOutcome outcome, ToolContext context) { return outcome; }

        @Override public Decision before(ToolCall call, ToolContext context) throws IOException, InterruptedException {
            if (isWrite(call.name())) {
                String path = guardedWritePath(call, context);
                if (path != null && protectedPath(path)) {
                    return Decision.deny(FailureCategory.PERMISSION,
                            "Path " + path + " is protected; workers may not change it.");
                }
            } else if ("finish".equals(call.name())) {
                for (String path : git.changedPaths(context.worktree())) {
                    if (protectedPath(path)) return Decision.hold(HoldClass.BOUNDARY_BREACHED,
                            "A protected repository path has uncommitted changes.");
                }
            }
            return Decision.allow();
        }

        private boolean protectedPath(String path) {
            if (!descriptor.allowWorkflowChanges() && matchesIgnoreCase(WORKFLOW_GLOB, path)) return true;
            return descriptor.protectedPathGlobs().stream().anyMatch(glob -> matchesIgnoreCase(glob, path));
        }
    }

    private static final class WriteBoundaryRule implements ToolCallInterceptor {
        private final EnforcementDescriptor descriptor;
        private final GitWorktreeManager git;

        private WriteBoundaryRule(EnforcementDescriptor descriptor, GitWorktreeManager git) {
            this.descriptor = descriptor;
            this.git = git;
        }

        @Override public String name() { return "write-boundary"; }

        @Override public ToolOutcome after(ToolCall call, ToolOutcome outcome, ToolContext context) { return outcome; }

        @Override public Decision before(ToolCall call, ToolContext context) throws IOException, InterruptedException {
            WriteBoundary boundary = new WriteBoundary(descriptor.writeBoundary(), descriptor.testPathGlobs());
            if (isWrite(call.name())) {
                String path = guardedWritePath(call, context);
                if (path == null || boundary.permits(path)) return Decision.allow();
                String message = boundary.requiresTestPaths()
                        ? "Path " + path + " is not a test file; this task writes tests only"
                        : "Path " + path + " is a test file; this task may not write tests";
                return Decision.deny(FailureCategory.PERMISSION, message);
            }
            if ("finish".equals(call.name())) {
                for (String path : git.changedPaths(context.worktree())) {
                    if (!boundary.permits(path)) return Decision.hold(HoldClass.BOUNDARY_BREACHED,
                            "An uncommitted path violates the task write boundary.");
                }
            }
            return Decision.allow();
        }
    }

    private static final class SecretContentRule implements ToolCallInterceptor {
        @Override public String name() { return "secret-content"; }

        @Override public ToolOutcome after(ToolCall call, ToolOutcome outcome, ToolContext context) { return outcome; }

        @Override public Decision before(ToolCall call, ToolContext context) {
            if (!isWrite(call.name())) return Decision.allow();
            String field = "write_file".equals(call.name()) ? "content" : "newText";
            String content = call.arguments().path(field).asText(null);
            return EvidenceRedactor.findCredentialToken(content)
                    .map(kind -> Decision.deny(FailureCategory.PERMISSION,
                            "The new text for " + pathArgument(call) + " contains a credential-shaped value (" + kind + "); write it without the credential."))
                    .orElseGet(Decision::allow);
        }
    }

    private static final class ResultRedactionRule implements ToolCallInterceptor {
        @Override public String name() { return "result-redaction"; }
        @Override public Decision before(ToolCall call, ToolContext context) { return Decision.allow(); }

        @Override public ToolOutcome after(ToolCall call, ToolOutcome outcome, ToolContext context) {
            if (!List.of("read_file", "search_files", "run_gate").contains(call.name())) return outcome;
            EvidenceRedactor.CredentialRedaction redaction = EvidenceRedactor.redactCredentialTokens(outcome.content());
            if (redaction.count() == 0) return outcome;
            Map<String, Object> meta = new LinkedHashMap<>(outcome.meta());
            meta.put("redactions", redaction.count());
            return new ToolOutcome(outcome.status(), outcome.category(), redaction.content(), meta, outcome.postImages());
        }
    }

    private static boolean isWrite(String name) { return "write_file".equals(name) || "edit_file".equals(name); }

    private static String pathArgument(ToolCall call) {
        JsonNode path = call.arguments().get("path");
        return path != null && path.isTextual() ? path.asText() : null;
    }

    private static String guardedWritePath(ToolCall call, ToolContext context) {
        String path = pathArgument(call);
        if (path == null) return null;
        try {
            // Normalize and confine first; the tool itself applies the stricter owned-prefix boundary.
            Path target = new WorktreePathGuard(context.worktree()).readable(path);
            return context.worktree().relativize(target).toString().replace('\\', '/');
        } catch (IllegalArgumentException invalidOrUnowned) {
            return null;
        }
    }

    private static String guardedInputPath(ToolCall call, ToolContext context) {
        String path = pathArgument(call);
        if (path == null) return null;
        try {
            WorktreePathGuard guard = new WorktreePathGuard(context.worktree());
            Path target = "search_files".equals(call.name()) ? guard.directory(path) : guard.readable(path);
            return context.worktree().relativize(target).toString().replace('\\', '/');
        } catch (IllegalArgumentException invalid) {
            return path;
        }
    }

    private static boolean matchesIgnoreCase(String glob, String path) {
        return glob != null && path != null && TestPathGlobs.matches(glob.toLowerCase(Locale.ROOT), path.toLowerCase(Locale.ROOT));
    }
}
