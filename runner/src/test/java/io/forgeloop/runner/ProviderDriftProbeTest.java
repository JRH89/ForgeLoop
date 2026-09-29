package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ProviderDriftProbeTest {
    private static final String FIXTURE = "{\"id\":\"fixture\",\"model\":\"gpt-test\",\"status\":\"completed\",\"usage\":{\"input_tokens\":1,\"output_tokens\":2},\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"recorded text\"}]}]}";

    @Test void requiresRecordedFixtureAndMatchingPinsBeforeConstructingTheProviderClient() {
        AtomicInteger constructions = new AtomicInteger();
        ProviderDriftProbe probe = new ProviderDriftProbe();
        ProviderDriftProbe.Result missing = probe.run("openai", "gpt-test", null, () -> {
            constructions.incrementAndGet(); throw new AssertionError("must not create provider without fixture");
        });
        assertEquals(2, missing.exitCode());
        assertEquals(0, constructions.get());

        ProviderFixtureStore.Fixture wrongPin = fixture("v2", "gpt-test");
        ProviderDriftProbe.Result changed = probe.run("openai", "gpt-test", wrongPin, () -> {
            constructions.incrementAndGet(); throw new AssertionError("must not call provider when fixture pins drift");
        });
        assertEquals(1, changed.exitCode());
        assertEquals(0, constructions.get());
    }

    @Test void makesOneRawHealthCallAndPrintsOnlyShapeDifferences() {
        AtomicInteger constructions = new AtomicInteger();
        AtomicInteger calls = new AtomicInteger();
        String live = "{\"id\":\"live\",\"model\":\"gpt-live\",\"status\":\"completed\",\"usage\":{\"input_tokens\":\"one\",\"output_tokens\":9},\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"secret live answer\"}]}]}";
        ProviderClient rawOnlyClient = new ProviderClient() {
            @Override public ProviderResult execute(ProviderRequest request) { throw new AssertionError("probe must not parse before comparing shape"); }
            @Override public String executeRaw(ProviderRequest request) {
                calls.incrementAndGet();
                assertEquals("gpt-test", request.model());
                assertEquals(128, request.maxOutputTokens());
                return live;
            }
        };

        ProviderDriftProbe.Result result = new ProviderDriftProbe().run("openai", "gpt-test", fixture("v1", "gpt-test"), () -> {
            constructions.incrementAndGet();
            return rawOnlyClient;
        });

        assertEquals(1, result.exitCode());
        assertEquals(1, constructions.get());
        assertEquals(1, calls.get());
        assertTrue(result.differences().contains("usage.input_tokens (expected number, got string)"));
        assertFalse(result.differences().toString().contains("secret live answer"));
    }

    @Test void rejectsARecordedFixtureForAnotherModelWithoutCallingTheProvider() {
        AtomicInteger calls = new AtomicInteger();
        ProviderDriftProbe.Result result = new ProviderDriftProbe().run("openai", "gpt-next", fixture("v1", "gpt-test"), () -> {
            calls.incrementAndGet(); return request -> null;
        });
        assertEquals(2, result.exitCode());
        assertEquals(0, calls.get());
    }

    private static ProviderFixtureStore.Fixture fixture(String apiVersion, String model) {
        return new ProviderFixtureStore.Fixture("openai-responses/1", "https://api.openai.com/v1/responses", apiVersion,
                model, "2026-09-29", FIXTURE);
    }
}
