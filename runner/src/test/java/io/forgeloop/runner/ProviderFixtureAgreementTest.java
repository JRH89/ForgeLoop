package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** Recorded-fixture agreement is enforced automatically once paid captures are available. */
class ProviderFixtureAgreementTest {
    private static final Map<String, String> TEXT_PATHS = Map.of(
            "anthropic", "content[].text",
            "openai", "output[].content[].text",
            "gemini", "candidates[].content.parts[].text",
            "local", "choices[].message.content");
    private static final Map<String, String> PINNED_VERSIONS = Map.of(
            "anthropic", "2023-06-01", "openai", "v1", "gemini", "v1beta", "local", "openai-chat-compatible");

    @Test
    void rejectsUnknownProviderBeforeUsingItAsAFixturePath() {
        assertThrows(IllegalArgumentException.class, () -> new ProviderFixtureStore().load("../../outside"));
    }

    @TestFactory Stream<DynamicTest> pinnedVersionsAndParserPathsAgreeWithCapturedFixtures() {
        return Stream.of("anthropic", "openai", "gemini", "local").map(provider ->
                DynamicTest.dynamicTest(provider, () -> assertFixtureAgreement(provider)));
    }

    private static void assertFixtureAgreement(String provider) throws Exception {
        ProviderFixtureStore.Fixture fixture = new ProviderFixtureStore().load(provider);
        Assumptions.assumeTrue(fixture != null, "Live provider fixture capture is pending; no canonical fixture was fabricated.");
        ProviderClientFactory factory = new ProviderClientFactory();
        assertEquals(factory.adapterId(provider), fixture.adapter());
        assertEquals(factory.pinnedEndpoint(provider), fixture.endpoint());
        assertEquals(factory.pinnedApiVersion(provider), fixture.apiVersion());
        assertEquals(PINNED_VERSIONS.get(provider), factory.pinnedApiVersion(provider));
        assertFalse(fixture.model().isBlank());
        assertFalse(fixture.recordedAt().isBlank());
        ProviderResult parsed = factory.parse(provider, fixture.responseBody());
        assertNotNull(parsed);
        assertFalse(parsed.output().isBlank());
        assertFalse(ProviderShape.of(provider, fixture.responseBody()).kinds().get(TEXT_PATHS.get(provider)).equals("missing"));
        assertPinIsAdapterContract(provider, factory);
    }

    private static void assertPinIsAdapterContract(String provider, ProviderClientFactory factory) {
        assertEquals(PINNED_VERSIONS.get(provider), factory.pinnedApiVersion(provider));
        if ("anthropic".equals(provider)) assertEquals(factory.pinnedApiVersion(provider), AnthropicMessagesProviderClient.API_VERSION);
    }
}
