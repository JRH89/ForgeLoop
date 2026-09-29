package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class EnforcementDescriptorTest {
    @Test
    void descriptorDependsOnlyOnDispatchedEnforcementFieldsAndFingerprintsPolicyChanges() {
        LoopGate gate = new LoopGate("verify", "eclipse-temurin:21", List.of("./mvnw", "test"), 300, false);
        RunnerTask first = new RunnerTask("one", "IMPLEMENTATION", "First title", "org/repo", "main", "main", "First spec", "git");
        RunnerTask second = new RunnerTask("two", "IMPLEMENTATION", "Different title", "org/repo", "main", "main", "Different spec", "git");

        EnforcementDescriptor firstDescriptor = EnforcementDescriptor.of(first, List.of(gate));
        EnforcementDescriptor secondDescriptor = EnforcementDescriptor.of(second, List.of(gate));

        assertEquals(firstDescriptor.sha256(), secondDescriptor.sha256());
        assertEquals(List.of("credential-files", "protected-paths", "secret-content"), firstDescriptor.ruleNames());
        assertEquals(List.of("credential-files", "result-redaction"), firstDescriptor.afterHookNames());
        assertNotEquals(firstDescriptor.sha256(), EnforcementDescriptor.fromDispatched("NO_TESTS",
                List.of("**/*Test.java"), null, List.of(), false, null, List.of("verify")).sha256());
    }

    @Test
    void invalidWireGlobIsPreservedAndHeldBeforeExecutionInsteadOfThrowing() {
        EnforcementDescriptor descriptor = EnforcementDescriptor.fromDispatched("TESTS_ONLY",
                List.of("../tests/**"), null, List.of(), false, null, List.of());

        assertEquals("../tests/**", descriptor.testPathGlobs().getFirst());
        var hold = EnforcementPreflight.check(descriptor, "a".repeat(40)).orElseThrow();
        assertEquals(HoldClass.RULE_INPUT_MISSING, hold.holdClass());
        assertEquals("enforcement-config", hold.check());
        assertTrue(hold.reason().contains("test-path globs"));
    }

    @Test
    void redPrerequisiteMustMatchBaseAndProtectedGlobsMustBeValid() {
        String baseSha = "a".repeat(40);
        RunnerRedPrerequisite red = new RunnerRedPrerequisite("test-task", "b".repeat(40), "c".repeat(64));
        EnforcementDescriptor mismatched = EnforcementDescriptor.fromDispatched("NO_TESTS", List.of("**/*Test.java"),
                red, List.of(), false, null, List.of());
        assertEquals(HoldClass.PREREQUISITE_MISSING,
                EnforcementPreflight.check(mismatched, baseSha).orElseThrow().holdClass());

        EnforcementDescriptor badProtectedPath = EnforcementDescriptor.fromDispatched("ANY", List.of(), null,
                List.of("../secrets/**"), false, null, List.of());
        assertEquals(HoldClass.RULE_INPUT_MISSING,
                EnforcementPreflight.check(badProtectedPath, baseSha).orElseThrow().holdClass());
    }

    @Test
    void redPrerequisiteRequiresCompleteCurrentProofForTheResolvedBase() {
        String baseSha = "a".repeat(40);
        String digest = "c".repeat(64);
        List<RunnerRedPrerequisite> invalid = List.of(
                new RunnerRedPrerequisite("test-task", null, digest),
                new RunnerRedPrerequisite("test-task", "b".repeat(40), digest),
                new RunnerRedPrerequisite("test-task", baseSha, null),
                new RunnerRedPrerequisite(null, baseSha, digest));

        for (RunnerRedPrerequisite prerequisite : invalid) {
            EnforcementDescriptor descriptor = EnforcementDescriptor.fromDispatched("NO_TESTS",
                    List.of("**/*Test.java"), prerequisite, List.of(), false, null, List.of());
            PolicyHold hold = EnforcementPreflight.check(descriptor, baseSha).orElseThrow();
            assertEquals(HoldClass.PREREQUISITE_MISSING, hold.holdClass());
            assertEquals("red-prerequisite", hold.check());
        }

        EnforcementDescriptor current = EnforcementDescriptor.fromDispatched("NO_TESTS", List.of("**/*Test.java"),
                new RunnerRedPrerequisite("test-task", baseSha, digest), List.of(), false, null, List.of());
        assertTrue(EnforcementPreflight.check(current, baseSha).isEmpty());
        EnforcementDescriptor notRequired = EnforcementDescriptor.fromDispatched("ANY", List.of(), null,
                List.of(), false, null, List.of());
        assertTrue(EnforcementPreflight.check(notRequired, baseSha).isEmpty());
    }

    @Test
    void taskDescriptorCarriesDispatchedRedProofIntoTheFingerprintedPolicy() {
        RunnerRedPrerequisite prerequisite = new RunnerRedPrerequisite("test-task", "a".repeat(40), "c".repeat(64));
        RunnerTask task = new RunnerTask("impl", "IMPLEMENTATION", "Implement", "org/repo", "main", "main",
                "spec", "provider", 1, List.of("src"), List.of(), null, null, null, List.of(), null, null,
                "main", "main", List.of(), List.of(), "NO_TESTS", List.of("**/*Test.java"), null,
                List.of(), false, null, prerequisite);

        EnforcementDescriptor descriptor = EnforcementDescriptor.of(task, List.of());

        assertEquals(prerequisite, descriptor.redPrerequisite());
        assertEquals(List.of("enforcement-config", "red-prerequisite"), descriptor.checkNames());
        assertEquals("2", descriptor.journalValue().get("rulesVersion"));
    }
}
