package io.forgeloop.control.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLedgerEntryRepository extends JpaRepository<AuditLedgerEntry, String> {
    List<AuditLedgerEntry> findByResourceTypeAndResourceIdOrderByOccurredAtAsc(String resourceType, String resourceId);
}
