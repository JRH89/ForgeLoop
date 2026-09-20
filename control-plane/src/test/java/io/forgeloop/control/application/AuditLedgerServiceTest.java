package io.forgeloop.control.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import io.forgeloop.control.domain.AuditLedgerEntry;
import io.forgeloop.control.domain.AuditLedgerEntryRepository;
import org.junit.jupiter.api.Test;

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
}
