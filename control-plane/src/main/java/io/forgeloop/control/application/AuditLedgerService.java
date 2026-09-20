package io.forgeloop.control.application;

import io.forgeloop.control.domain.AuditLedgerEntry;
import io.forgeloop.control.domain.AuditLedgerEntryRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** Records action metadata without persisting specifications, credentials, command output, or source content. */
@Service
public class AuditLedgerService {
    private final AuditLedgerEntryRepository entries;
    public AuditLedgerService(AuditLedgerEntryRepository entries) { this.entries = entries; }
    public void record(String action, String resourceType, String resourceId, String material) {
        entries.save(new AuditLedgerEntry(actor(), action, resourceType, resourceId, digest(material)));
    }
    /** Returns the immutable history for an already-authorized resource. */
    public List<AuditLedgerEntry> events(String resourceType, String resourceId) {
        return entries.findByResourceTypeAndResourceIdOrderByOccurredAtAsc(resourceType, resourceId);
    }
    private static String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() ? authentication.getName() : "development-anonymous";
    }
    private static String digest(String material) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException("SHA-256 unavailable", exception); }
    }
}
