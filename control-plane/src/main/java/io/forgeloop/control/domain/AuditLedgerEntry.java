package io.forgeloop.control.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Instant;

/** Append-only, digest-only record of a security-relevant control-plane action. */
@Entity
public class AuditLedgerEntry {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false, length = 255) private String actor;
    @Column(nullable = false, length = 100) private String action;
    @Column(nullable = false, length = 100) private String resourceType;
    @Column(nullable = false, length = 255) private String resourceId;
    @Column(nullable = false, length = 64) private String payloadDigest;
    @Column(nullable = false) private Instant occurredAt;

    protected AuditLedgerEntry() { }
    public AuditLedgerEntry(String actor, String action, String resourceType, String resourceId, String payloadDigest) {
        this.actor = actor; this.action = action; this.resourceType = resourceType; this.resourceId = resourceId;
        this.payloadDigest = payloadDigest; this.occurredAt = Instant.now();
    }
    public String getId() { return id; } public String getActor() { return actor; } public String getAction() { return action; }
    public String getResourceType() { return resourceType; } public String getResourceId() { return resourceId; }
    public String getPayloadDigest() { return payloadDigest; } public String getOccurredAt() { return occurredAt.toString(); }
}
