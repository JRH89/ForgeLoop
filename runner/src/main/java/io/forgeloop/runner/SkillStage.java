package io.forgeloop.runner;

import java.util.Optional;
import java.util.Set;

/** Procedures are routed by server-owned roles; deterministic stages never acquire agent procedures. */
public enum SkillStage {
    AUTONOMOUS_SPECIFICATION, INTERACTIVE_SPECIFICATION, PLANNING,
    IMPLEMENTATION, INDEPENDENT_TEST, REPAIR, REVIEW;

    private static final Set<String> DETERMINISTIC = Set.of("INTEGRATION", "VERIFICATION", "RED_CHECK", "GREEN_CHECK",
            "PR_PREPARATION", "MERGE", "REPOSITORY_SCAN");

    public static Optional<SkillStage> forRole(String role) {
        if (role == null) throw new IllegalArgumentException("A skill role is required");
        return switch (role) {
            case "ISSUE_SPECIFICATION" -> Optional.of(AUTONOMOUS_SPECIFICATION);
            case "AI_CHAT" -> Optional.of(INTERACTIVE_SPECIFICATION);
            case "PLANNER" -> Optional.of(PLANNING);
            case "IMPLEMENTATION", "BACKEND", "FRONTEND" -> Optional.of(IMPLEMENTATION);
            case "INDEPENDENT_TEST" -> Optional.of(INDEPENDENT_TEST);
            case "REPAIR" -> Optional.of(REPAIR);
            case "REVIEW" -> Optional.of(REVIEW);
            default -> {
                if (!DETERMINISTIC.contains(role)) throw new IllegalArgumentException("Unknown skill role");
                yield Optional.empty();
            }
        };
    }
}
