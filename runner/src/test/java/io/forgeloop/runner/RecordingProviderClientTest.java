package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordingProviderClientTest {
    @TempDir Path temporary;

    @Test void recordsEachRetryRequestAndVerbatimResponseWithWireDigest() throws Exception {
        StepJournal journal = new StepJournal(temporary, "task-1", "lease-1", Clock.systemUTC());
        AtomicInteger calls = new AtomicInteger();
        ProviderClient provider = request -> {
            if (calls.getAndIncrement() == 0) throw new ProviderException("temporary outage", true);
            return new ProviderResult("{}", 4, 2, "req-1", "model-2026", "{\"model\":\"model-2026\"}");
        };
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "model", 2);
        ProviderRequest request = new ProviderRequest("model", "rules", "input", 1024, StructuredOutputSchemas.plan());

        ProviderExecutionResult result = new ProviderExecutionService().executeDetailed(
                new RecordingProviderClient(provider, policy, journal), request, 2);

        assertEquals(2, result.attemptCount());
        List<com.fasterxml.jackson.databind.JsonNode> records = journal.records();
        assertEquals(List.of("CALL_REQUESTED", "CALL_FAILED", "CALL_REQUESTED", "CALL_COMPLETED"),
                records.stream().map(row -> row.path("type").asText()).toList());
        String exactBody = new ProviderClientFactory().requestBody("openai", request);
        assertEquals(sha256(exactBody), records.get(0).path("requestSha256").asText());
        assertEquals("rules", records.get(0).path("instructions").asText());
        assertEquals("temporary outage", records.get(1).path("summary").asText());
        assertEquals("{\"model\":\"model-2026\"}", records.get(3).path("responseBody").asText());
        assertEquals("model-2026", records.get(3).path("answeredModel").asText());
        assertFalse(records.get(3).has("apiKey"));
    }

    @Test void adaptersRetainTheExactResponseBodyForRecording() throws Exception {
        String body = "{\"id\":\"r1\",\"model\":\"gpt-dated\",\"usage\":{\"input_tokens\":1,\"output_tokens\":1},\"output\":[{\"content\":[{\"type\":\"output_text\",\"text\":\"ok\"}]}]}";
        assertEquals(body, OpenAiResponsesProviderClient.parse(body).responseBody());
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    }
}
