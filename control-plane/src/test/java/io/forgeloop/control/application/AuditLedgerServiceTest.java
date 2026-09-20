package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.forgeloop.control.domain.AuditLedgerEntry;
import io.forgeloop.control.domain.AuditLedgerEntryRepository;
import org.junit.jupiter.api.Test;
import java.util.List;

class AuditLedgerServiceTest {
    @Test
    void storesDigestInsteadOfRawActionMaterial() {
        AuditLedgerEntryRepository entries = mock(AuditLedgerEntryRepository.class);
        new AuditLedgerService(entries).record("FEATURE_RUN_SUBMITTED", "FEATURE_RUN", "run-1", "private specification");

        var entry = forClass(AuditLedgerEntry.class);
        verify(entries).save(entry.capture());
        assertEquals("FEATURE_RUN_SUBMITTED", entry.getValue().getAction());
        assertEquals(64, entry.getValue().getPayloadDigest().length());
    }
    @Test
    void readsOrderedResourceHistory() {
        AuditLedgerEntryRepository entries = mock(AuditLedgerEntryRepository.class);
        AuditLedgerEntry entry = new AuditLedgerEntry("operator", "FEATURE_RUN_SUBMITTED", "FEATURE_RUN", "run-1", "a".repeat(64));
        when(entries.findByResourceTypeAndResourceIdOrderByOccurredAtAsc("FEATURE_RUN", "run-1")).thenReturn(List.of(entry));
        assertEquals(List.of(entry), new AuditLedgerService(entries).events("FEATURE_RUN", "run-1"));
    }
}
