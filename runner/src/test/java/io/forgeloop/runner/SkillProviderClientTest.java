package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SkillProviderClientTest {
    @TempDir Path temporary;
    private static SkillSelection selection(String role) {
        return new StageSkillResolver(StageSkillResolver.PROFILE, Set.of("org/repo")).resolveRole(role, "org/repo");
    }
    private static ProviderRequest request() {
        return new ProviderRequest("model", "JSON only; unchanged authority", "untrusted user text", 512,
                new ObjectMapper().createObjectNode().put("type", "object"));
    }

    @Test void selectedInstructionsAreInjectedWithoutChangingInputSchemaOrLimits() throws Exception {
        AtomicReference<ProviderRequest> captured = new AtomicReference<>();
        SkillSelection selection = selection("PLANNER");
        ProviderRequest original = request();
        new SkillProviderClient(value -> { captured.set(value); return new ProviderResult("{}", 1, 1, "id"); }, selection).execute(original);
        ProviderRequest actual = captured.get();
        assertTrue(actual.instructions().contains("# Structured planning"));
        assertEquals(original.input(), actual.input());
        assertEquals(original.maxOutputTokens(), actual.maxOutputTokens());
        assertEquals(original.outputSchema(), actual.outputSchema());
        assertEquals(selection.metadata(), actual.skillSelection().metadata());
        String body = new ProviderClientFactory().requestBody("openai", actual);
        assertFalse(body.contains("selectionSha256"), "Skill metadata must not become an unsupported provider wire field");
        assertTrue(body.contains("Structured planning"));
    }

    @Test void disabledSelectionPreservesRequestIdentityAndSelectionsCannotBeStacked() throws Exception {
        ProviderRequest original = request();
        AtomicReference<ProviderRequest> captured = new AtomicReference<>();
        new SkillProviderClient(value -> { captured.set(value); return new ProviderResult("{}", 1, 1, "id"); }, SkillSelection.none()).execute(original);
        assertSame(original, captured.get());
        ProviderRequest enhanced = original.withSkills(selection("PLANNER"));
        assertSame(enhanced, enhanced.withSkills(selection("PLANNER")));
        assertThrows(IllegalArgumentException.class, () -> enhanced.withSkills(selection("REVIEW")));
        assertThrows(IllegalArgumentException.class, () -> selection("PLANNER").requireRole("REVIEW", "org/repo"));
        assertThrows(IllegalArgumentException.class, () -> selection("PLANNER").requireRole("PLANNER", "other/repo"));
    }

    @Test void recordingPinsTheSameSelectionAndComposedInstructionsAsTheProviderRequest() throws Exception {
        StepJournal journal = new StepJournal(temporary, "task", "lease", Clock.systemUTC());
        var policy = new ProviderExecutionPolicy("openai", "model", 1);
        RecordingProviderClient recorder = new RecordingProviderClient(value -> new ProviderResult("{}", 1, 1, "id"), policy, journal);
        SkillSelection selected = selection("REPAIR");
        new SkillProviderClient(recorder, selected).execute(request());
        var recorded = journal.records().getFirst();
        assertEquals(selected.metadata().get("selectionSha256"), recorded.path("skills").path("selectionSha256").asText());
        assertEquals(selected.composeInstructions(request().instructions()), recorded.path("instructions").asText());
        assertFalse(recorded.path("skills").toString().contains("Read the structured failure"));
    }
}
