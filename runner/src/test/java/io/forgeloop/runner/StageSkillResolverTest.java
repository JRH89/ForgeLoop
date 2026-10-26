package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StageSkillResolverTest {
    private static final String REPOSITORY = "org/repository";
    private static StageSkillResolver enabled() { return new StageSkillResolver(StageSkillResolver.PROFILE, Set.of(REPOSITORY)); }

    @Test void disabledAndUnselectedRepositoriesKeepTheOriginalInstructions() {
        String base = "Original output contract";
        assertSame(base, new StageSkillResolver("", Set.of()).resolveRole("PLANNER", REPOSITORY).composeInstructions(base));
        assertFalse(enabled().resolveRole("PLANNER", "other/repository").enabled());
        assertFalse(StageSkillResolver.fromEnvironment(Map.of()).resolveRole("PLANNER", REPOSITORY).enabled());
    }

    @Test void routesOnlyTheProceduresForEachRole() {
        StageSkillResolver resolver = enabled();
        assertEquals(List.of("structured-planning"), resolver.resolveRole("PLANNER", REPOSITORY).skillIds());
        assertEquals(List.of("scoped-implementation", "evidence-based-completion"), resolver.resolveRole("FRONTEND", REPOSITORY).skillIds());
        assertEquals(List.of("scoped-implementation", "systematic-repair", "evidence-based-completion"), resolver.resolveRole("REPAIR", REPOSITORY).skillIds());
        assertEquals(List.of("independent-test-design", "evidence-based-completion"), resolver.resolveRole("INDEPENDENT_TEST", REPOSITORY).skillIds());
        assertEquals(List.of("independent-review", "evidence-based-completion"), resolver.resolveRole("REVIEW", REPOSITORY).skillIds());
        assertEquals(List.of("autonomous-specification"), resolver.resolveRole("ISSUE_SPECIFICATION", REPOSITORY).skillIds());
        assertEquals(List.of("interactive-specification"), resolver.resolveRole("AI_CHAT", REPOSITORY).skillIds());
        for (String role : List.of("INTEGRATION", "VERIFICATION", "RED_CHECK", "GREEN_CHECK", "PR_PREPARATION", "MERGE", "REPOSITORY_SCAN"))
            assertFalse(resolver.resolveRole(role, REPOSITORY).enabled(), role);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolveRole("invented", REPOSITORY));
    }

    @Test void invalidConfigurationFailsClosedAndRequiresAnExactRepositoryAllowlist() {
        assertThrows(IllegalArgumentException.class, () -> new StageSkillResolver("unknown", Set.of(REPOSITORY)));
        assertThrows(IllegalArgumentException.class, () -> new StageSkillResolver(StageSkillResolver.PROFILE, Set.of()));
        assertThrows(IllegalArgumentException.class, () -> new StageSkillResolver(StageSkillResolver.PROFILE, Set.of("org/*")));
        assertThrows(IllegalArgumentException.class, () -> StageSkillResolver.fromEnvironment(Map.of(
                "FORGELOOP_SKILL_PROFILE", StageSkillResolver.PROFILE, "FORGELOOP_SKILL_REPOSITORIES", REPOSITORY + ",")));
        assertTrue(StageSkillResolver.fromEnvironment(Map.of("FORGELOOP_SKILL_PROFILE", StageSkillResolver.PROFILE,
                "FORGELOOP_SKILL_REPOSITORIES", REPOSITORY)).resolveRole("PLANNER", REPOSITORY).enabled());
    }

    @Test void pinsStableImmutableMetadataWithoutRawInstructions() {
        SkillSelection first = enabled().resolveRole("BACKEND", REPOSITORY);
        SkillSelection second = enabled().resolveRole("BACKEND", REPOSITORY);
        assertTrue(first.enabled());
        assertEquals(first.metadata(), second.metadata());
        assertNotEquals(first.metadata(), enabled().resolveRole("REPAIR", REPOSITORY).metadata());
        assertTrue(first.metadata().get("selectionSha256").toString().matches("[0-9a-f]{64}"));
        assertThrows(UnsupportedOperationException.class, () -> first.metadata().put("profile", "evil"));
        assertThrows(UnsupportedOperationException.class, () -> first.skillIds().add("merge"));
        String instructions = first.composeInstructions("OUTPUT CONTRACT");
        assertTrue(instructions.startsWith("OUTPUT CONTRACT"));
        assertTrue(instructions.contains("# Scoped implementation"));
        assertFalse(instructions.contains("# Systematic repair"));
        assertFalse(first.metadata().toString().contains("Read the assigned task"));
        assertTrue(instructions.length() < 24 * 1024);
    }

    @Test void missingChangedOversizedAndRecursiveResourcesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> resolver(path -> null).resolveRole("PLANNER", REPOSITORY));
        assertThrows(IllegalArgumentException.class, () -> resolver(path -> path.endsWith("SKILL.md")
                ? bytes("Modified instructions") : resource(path)).resolveRole("PLANNER", REPOSITORY));
        assertThrows(IllegalArgumentException.class, () -> resolver(path -> path.endsWith("manifest.json")
                ? bytes("x".repeat(40_000)) : resource(path)).resolveRole("PLANNER", REPOSITORY));
        assertThrows(IllegalArgumentException.class, () -> resolver(path -> {
            if (!path.endsWith("manifest.json")) return resource(path);
            try (InputStream input = resource(path)) {
                return bytes(new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\"skills\":", "\"dependencies\": [\"remote-skill\"], \"skills\":"));
            } catch (Exception invalid) { throw new IllegalArgumentException(invalid); }
        }).resolveRole("PLANNER", REPOSITORY));
    }

    @Test void manifestCannotAddToolsUnknownStagesDuplicateKeysOrPathTraversal() {
        for (String mutation : List.of("tools", "stage", "duplicate", "path")) {
            StageSkillResolver resolver = resolver(path -> {
                if (!path.endsWith("manifest.json")) return resource(path);
                try (InputStream input = resource(path)) {
                    String manifest = new String(input.readAllBytes(), StandardCharsets.UTF_8);
                    manifest = switch (mutation) {
                        case "tools" -> manifest.replace("\"skills\":", "\"tools\": [\"shell\"], \"skills\":");
                        case "stage" -> manifest.replace("\"PLANNING\"", "\"MERGE\"");
                        case "duplicate" -> manifest.replace("\"profile\":", "\"profile\": \"other\", \"profile\":");
                        default -> manifest.replace("structured-planning", "../outside");
                    };
                    return bytes(manifest);
                } catch (Exception invalid) { throw new IllegalArgumentException(invalid); }
            });
            assertThrows(IllegalArgumentException.class, () -> resolver.resolveRole("PLANNER", REPOSITORY), mutation);
        }
    }

    @Test void disabledAndDeterministicStagesNeverReadResources() {
        var resolver = resolver(path -> { fail("This role/repository must not load a skill pack"); return null; });
        assertFalse(resolver.resolveRole("MERGE", REPOSITORY).enabled());
        assertFalse(resolver.resolveRole("PLANNER", "other/repo").enabled());
        var disabled = new StageSkillResolver("", Set.of(), path -> { fail("Disabled pack was loaded"); return null; });
        assertFalse(disabled.resolveRole("PLANNER", REPOSITORY).enabled());
    }

    private static StageSkillResolver resolver(java.util.function.Function<String, InputStream> source) {
        return new StageSkillResolver(StageSkillResolver.PROFILE, Set.of(REPOSITORY), source);
    }
    private static InputStream resource(String path) { return StageSkillResolverTest.class.getResourceAsStream(path); }
    private static InputStream bytes(String text) { return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)); }
}
