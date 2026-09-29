package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProviderFixtureCaptureTest {
    private static final String LIVE_SHAPED_RESPONSE = """
            {"id":"msg_live","type":"message","role":"assistant","model":"claude-sonnet-5",
             "content":[{"type":"text","text":"ForgeLoop provider ready."}],"stop_reason":"end_turn",
             "stop_sequence":null,"usage":{"input_tokens":12,"output_tokens":5}}
            """;

    @TempDir Path fixtureRoot;

    @Test void makesOneFixedBoundedRequestAndStoresTheResponseWithAdapterPins() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        ProviderClient client = new ProviderClient() {
            @Override public ProviderResult execute(ProviderRequest request) { throw new AssertionError("capture must retain the raw body"); }
            @Override public String executeRaw(ProviderRequest request) {
                calls.incrementAndGet();
                assertEquals("claude-sonnet-5", request.model());
                assertEquals(128, request.maxOutputTokens());
                assertEquals("Reply with exactly: ForgeLoop provider ready.", request.input());
                return LIVE_SHAPED_RESPONSE;
            }
        };

        ProviderFixtureCapture.Captured captured = new ProviderFixtureCapture().capture("anthropic", "claude-sonnet-5",
                () -> client, fixtureRoot, Instant.parse("2026-09-29T12:00:00Z"));

        assertEquals(1, calls.get());
        assertEquals(12, captured.inputTokens());
        assertEquals(5, captured.outputTokens());
        assertEquals(LIVE_SHAPED_RESPONSE, Files.readString(captured.directory().resolve("response.json")));
        String metadata = Files.readString(captured.directory().resolve("meta.json"));
        assertTrue(metadata.contains("anthropic-messages/1"));
        assertTrue(metadata.contains("https://api.anthropic.com/v1/messages"));
        assertTrue(metadata.contains("2023-06-01"));
        assertTrue(metadata.contains("claude-sonnet-5"));
        assertTrue(metadata.contains("2026-09-29T12:00:00Z"));
    }

    @Test void refusesAnExistingFixtureBeforeConstructingAClient() throws Exception {
        Path existing = Files.createDirectory(fixtureRoot.resolve("anthropic"));
        AtomicInteger constructions = new AtomicInteger();

        assertThrows(java.io.IOException.class, () -> new ProviderFixtureCapture().capture("anthropic", "claude-sonnet-5",
                () -> { constructions.incrementAndGet(); throw new AssertionError("must not call for an existing fixture"); },
                fixtureRoot, Instant.now()));

        assertEquals(0, constructions.get());
        assertFalse(Files.exists(existing.resolve("response.json")));
    }

    @Test void rejectsNonTextResponsesWithoutWritingCanonicalFiles() {
        ProviderClient client = new ProviderClient() {
            @Override public ProviderResult execute(ProviderRequest request) { throw new AssertionError("capture must retain the raw body"); }
            @Override public String executeRaw(ProviderRequest request) { return "{\"content\":[],\"usage\":{}}"; }
        };

        assertThrows(IllegalArgumentException.class, () -> new ProviderFixtureCapture().capture("anthropic", "claude-sonnet-5",
                () -> client, fixtureRoot, Instant.now()));

        assertFalse(Files.exists(fixtureRoot.resolve("anthropic")));
    }
}
