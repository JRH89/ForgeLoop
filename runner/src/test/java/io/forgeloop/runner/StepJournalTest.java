package io.forgeloop.runner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StepJournalTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    @TempDir Path temporaryDirectory;

    @Test
    void appendsForcedMonotonicHashChainedRecords() throws Exception {
        StepJournal journal = new StepJournal(temporaryDirectory, "task-1", "lease-1", Clock.systemUTC());
        journal.append("LOOP_STARTED", Map.of("role", "IMPLEMENTATION"));
        journal.append("USER_MESSAGE", Map.of("content", "sensitive local data"));
        List<JsonNode> records = journal.records();
        assertEquals(2, records.size());
        assertEquals(1, records.get(0).path("seq").asInt());
        assertEquals("0".repeat(64), records.get(0).path("prev").asText());
        assertEquals(Hashing.sha256(Files.readAllLines(journal.path()).get(0).getBytes(StandardCharsets.UTF_8)), records.get(1).path("prev").asText());
        assertEquals("lease-1", records.get(1).path("leaseId").asText());
        try {
            assertEquals(java.util.EnumSet.of(java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE), Files.getPosixFilePermissions(journal.path()));
        } catch (UnsupportedOperationException ignored) {
            // Windows relies on the owning account and host ACL policy.
        }
    }

    @Test
    void dropsOnlyATornFinalLineAndContinuesSequence() throws Exception {
        StepJournal journal = new StepJournal(temporaryDirectory, "task-2", "lease-1", Clock.systemUTC());
        journal.append("LOOP_STARTED", Map.of("role", "IMPLEMENTATION"));
        Files.writeString(journal.path(), "{\"seq\":2,\"torn\":", StandardOpenOption.APPEND);
        StepJournal recovered = new StepJournal(temporaryDirectory, "task-2", "lease-2", Clock.systemUTC());
        assertEquals(1, recovered.records().size());
        recovered.append("LOOP_STARTED", Map.of("role", "REPAIR"));
        assertEquals(2, recovered.records().size());
        assertTrue(Files.readString(recovered.path()).endsWith("\n"));
    }

    @Test
    void refusesAChangedCompleteRecord() throws Exception {
        StepJournal journal = new StepJournal(temporaryDirectory, "task-3", "lease-1", Clock.systemUTC());
        journal.append("LOOP_STARTED", Map.of("role", "IMPLEMENTATION"));
        journal.append("USER_MESSAGE", Map.of("content", "task text"));
        String record = Files.readString(journal.path()).replace("IMPLEMENTATION", "REPAIR");
        Files.writeString(journal.path(), record, StandardOpenOption.TRUNCATE_EXISTING);
        assertThrows(java.io.IOException.class, () -> new StepJournal(temporaryDirectory, "task-3", "lease-2", Clock.systemUTC()));
    }
}
