package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReplayProviderClientTest {
    @TempDir Path temporary;
    private static final String RESPONSE = "{\"id\":\"resp-1\",\"model\":\"gpt-test\",\"usage\":{\"input_tokens\":4,\"output_tokens\":2},\"output\":[{\"type\":\"message\",\"content\":[{\"type\":\"output_text\",\"text\":\"recorded answer\"}]}]}";

    @Test void replaysRecordedRetryThenSuccessWithProductionParserAndNoCredentialedClient() throws Exception {
        StepJournal journal = new StepJournal(temporary, "retry-task", "lease-1", Clock.systemUTC());
        AtomicInteger attempts = new AtomicInteger();
        ProviderClient recording = new RecordingProviderClient(request -> {
            if (attempts.getAndIncrement() == 0) throw new ProviderException("temporary outage", true);
            try { return OpenAiResponsesProviderClient.parse(RESPONSE); }
            catch (Exception failure) { throw new ProviderException("test provider response was invalid", false, failure); }
        }, new ProviderExecutionPolicy("openai", "gpt-test", 2), journal);
        ProviderRequest request = new ProviderRequest("gpt-test", "rules", "input", 128);
        ProviderResult recorded = new ProviderExecutionService().execute(recording, request, 2);
        ReplayProviderClient replay = new ReplayProviderClient("openai", JournalFile.open(journal.path()));

        ProviderExecutionResult replayed = new ProviderExecutionService().executeDetailed(replay, request, 2);

        assertEquals(2, replayed.attemptCount());
        assertEquals(recorded.output(), replayed.result().output());
        assertEquals(recorded.responseBody(), replayed.result().responseBody());
        assertEquals("recorded answer", replayed.result().output());
        assertThrows(ReplayDivergence.class, () -> replay.execute(request));
    }

    @Test void changedRequestsDivergeWithoutAdvancingAndRecordedFailuresKeepRetryability() throws Exception {
        StepJournal journal = new StepJournal(temporary, "failure-task", "lease-2", Clock.systemUTC());
        ProviderRequest request = new ProviderRequest("gpt-test", "rules", "input", 128);
        ProviderExecutionPolicy policy = new ProviderExecutionPolicy("openai", "gpt-test", 1);
        ProviderClient recording = new RecordingProviderClient(ignored -> { throw new ProviderException("recorded failure", false); }, policy, journal);
        assertThrows(ProviderException.class, () -> recording.execute(request));

        ReplayProviderClient replay = new ReplayProviderClient("openai", JournalFile.open(journal.path()));
        ReplayDivergence divergence = assertThrows(ReplayDivergence.class,
                () -> replay.execute(new ProviderRequest("gpt-test", "rules", "changed", 128)));
        assertTrue(divergence.getMessage().contains("call 1: request digest differs"));
        ProviderException failure = assertThrows(ProviderException.class, () -> replay.execute(request));
        assertFalse(failure.retryable());
        assertEquals("Replayed recorded provider failure", failure.getMessage());
    }

    @Test void journalFileVerifiesRawHashChainAndIgnoresOnlyAnIncompleteTail() throws Exception {
        StepJournal journal = new StepJournal(temporary, "chain-task", "lease-3", Clock.systemUTC());
        journal.append("WORKER_STARTED", java.util.Map.of("role", "planner"));
        journal.append("WORKER_ENDED", java.util.Map.of("category", "DONE"));
        Files.writeString(journal.path(), "{unfinished", java.nio.file.StandardOpenOption.APPEND);
        assertEquals(2, JournalFile.open(journal.path()).records().size());

        List<String> lines = Files.readAllLines(journal.path()).subList(0, 2);
        lines.set(0, lines.get(0).replace("planner", "reviewer"));
        Files.writeString(journal.path(), String.join("\n", lines) + "\n");
        assertThrows(java.io.IOException.class, () -> JournalFile.open(journal.path()));
    }

    @Test void refusesAlteredOrUnpairedProviderRecords() throws Exception {
        StepJournal journal = new StepJournal(temporary, "partial-task", "lease-4", Clock.systemUTC());
        journal.append("CALL_REQUESTED", java.util.Map.of("requestSha256", "0".repeat(64), "call", 1, "try", 1));
        assertThrows(IllegalArgumentException.class, () -> new ReplayProviderClient("openai", JournalFile.open(journal.path())));
        assertEquals(List.of("CALL_REQUESTED"), JournalFile.open(journal.path()).records().stream().map(r -> r.path("type").asText()).toList());
    }
}
