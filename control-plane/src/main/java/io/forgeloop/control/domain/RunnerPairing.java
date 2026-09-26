package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Only a desktop proof hash is persisted; approval cannot reveal a runner credential. */
@Entity @Table(name="runner_pairing")
public class RunnerPairing {
    @Id private String challenge;
    @Column(nullable=false) private String organizationId;
    @Column(nullable=false, length=100) private String name;
    @Column(nullable=false) private Instant expiresAt;
    private Instant consumedAt;
    protected RunnerPairing() { }
    public RunnerPairing(String challenge, String organizationId, String name, Instant expiresAt) {
        this.challenge=challenge; this.organizationId=organizationId; this.name=name; this.expiresAt=expiresAt;
    }
    public void consume(Instant now) {
        if (consumedAt!=null || !now.isBefore(expiresAt)) throw new IllegalStateException("Pairing expired or already used; reconnect from the runner");
        consumedAt=now;
    }
    public String getOrganizationId() { return organizationId; }
    public String getName() { return name; }
}
