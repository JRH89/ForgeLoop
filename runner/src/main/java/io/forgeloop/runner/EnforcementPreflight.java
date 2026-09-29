package io.forgeloop.runner;

import java.util.List;
import java.util.Optional;

/** Validates all dispatch-provided enforcement inputs before the first provider request. */
public final class EnforcementPreflight {
    private EnforcementPreflight() { }

    public static Optional<PolicyHold> check(EnforcementDescriptor descriptor, String baseSha) {
        if (descriptor == null) return Optional.of(missing("Enforcement descriptor is missing"));
        String boundary = descriptor.writeBoundary();
        List<String> tests = descriptor.testPathGlobs();
        boolean validMode = List.of("ANY", "TESTS_ONLY", "NO_TESTS").contains(boundary);
        if (!validMode) return Optional.of(missing("Dispatched write-boundary mode is invalid"));
        if (tests == null || ("ANY".equals(boundary) && !tests.isEmpty())
                || (!"ANY".equals(boundary) && !TestPathGlobs.areValid(tests))) {
            return Optional.of(missing("Dispatched test-path globs do not match the write-boundary mode"));
        }
        List<String> protectedPaths = descriptor.protectedPathGlobs();
        if (protectedPaths == null || protectedPaths.size() > 64
                || protectedPaths.stream().anyMatch(glob -> !TestPathGlobs.isValid(glob))) {
            return Optional.of(missing("Dispatched protected-path globs are invalid"));
        }
        if (descriptor.allowWorkflowChanges() == null) {
            return Optional.of(missing("Dispatched workflow-path policy is missing"));
        }
        if (descriptor.gateNames() == null) return Optional.of(missing("Dispatched gate names are missing"));
        if (descriptor.finishGate() != null && (descriptor.finishGate().isBlank()
                || !descriptor.gateNames().contains(descriptor.finishGate()))) {
            return Optional.of(missing("Configured finish gate was not dispatched"));
        }
        RunnerRedPrerequisite prerequisite = descriptor.redPrerequisite();
        if (prerequisite != null) {
            if (!"NO_TESTS".equals(boundary)) return Optional.of(missing("RED prerequisite requires a no-tests write boundary"));
            if (baseSha == null || !baseSha.matches("[0-9a-f]{40,64}")
                    || prerequisite.testTaskId() == null || prerequisite.testTaskId().isBlank()
                    || prerequisite.targetSha() == null || !prerequisite.targetSha().matches("[0-9a-f]{40,64}")
                    || prerequisite.evidenceDigest() == null || !prerequisite.evidenceDigest().matches("[0-9a-f]{64}")
                    || !baseSha.equals(prerequisite.targetSha())) {
                return Optional.of(new PolicyHold(HoldClass.PREREQUISITE_MISSING, "red-prerequisite",
                        "Current passing RED evidence for the task base commit is missing or inconsistent"));
            }
        }
        return Optional.empty();
    }

    private static PolicyHold missing(String reason) {
        return new PolicyHold(HoldClass.RULE_INPUT_MISSING, "enforcement-config", reason);
    }
}
