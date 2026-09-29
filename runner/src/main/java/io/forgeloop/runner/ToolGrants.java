package io.forgeloop.runner;

import java.util.LinkedHashSet;
import java.util.Set;

/** Maps role policy grants to a stable, explicit tool surface. */
public final class ToolGrants {
    private ToolGrants() { }

    public static Set<String> forRole(String role, boolean hasGates) {
        WorkerRolePolicy policy = WorkerRolePolicy.require(role);
        Set<String> tools = new LinkedHashSet<>();
        for (String grant : policy.tools()) {
            switch (grant) {
                case "repository-read" -> tools.addAll(Set.of("list_files", "read_file", "search_files"));
                case "scoped-file-write" -> tools.addAll(Set.of("write_file", "edit_file"));
                case "git-commit" -> tools.add("finish");
                case "gate-run" -> { if (hasGates) tools.add("run_gate"); }
                // Evidence reading is already supplied in the repair specification; no extra tool is granted.
                default -> { }
            }
        }
        return Set.copyOf(tools);
    }
}
