package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Organization ownership for an installed GitHub App; never trust an installation id supplied by an operator. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = "installationId"))
public class GithubInstallation {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @Column(nullable = false) private long installationId;
    @Column(nullable = false) private String organizationId;
    @Column(nullable = false) private Instant installedAt;
    protected GithubInstallation() { }
    public GithubInstallation(long installationId, String organizationId) { this.installationId = installationId; this.organizationId = organizationId; installedAt = Instant.now(); }
    public long getInstallationId() { return installationId; } public String getOrganizationId() { return organizationId; }
}
