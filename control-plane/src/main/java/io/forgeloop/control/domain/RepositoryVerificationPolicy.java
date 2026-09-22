package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.util.List;

/** Versioned verification command owned by a repository connection. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"connection_id", "name"}))
public class RepositoryVerificationPolicy {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private RepositoryConnection connection;
    @Column(nullable = false, length = 80) private String name;
    @Column(nullable = false, length = 40) private String kind;
    @Column(nullable = false, length = 255) private String imageDigest;
    @Column(nullable = false, length = 8000) private String command;
    @Column(nullable = false, length = 20) private String networkPolicy;
    private int timeoutSeconds;
    private boolean required;
    @Column(nullable = false, length = 20) private String criterionCoverage;

    protected RepositoryVerificationPolicy() { }
    RepositoryVerificationPolicy(RepositoryConnection connection, VerificationPolicySpec spec) {
        this.connection = connection; this.name = spec.name(); apply(spec);
    }
    boolean matches(String candidate) { return name.equals(candidate); }
    void apply(VerificationPolicySpec spec) { if (!name.equals(spec.name())) throw new IllegalArgumentException("Verification policy identity cannot change");this.kind = spec.kind(); this.imageDigest = spec.imageDigest();this.command = String.join("\n", spec.command()); this.networkPolicy = spec.networkPolicy();this.timeoutSeconds = spec.timeoutSeconds(); this.required = spec.required(); this.criterionCoverage = spec.criterionCoverage(); }
    public VerificationPolicySpec toSpec() { return new VerificationPolicySpec(name, kind, imageDigest, command.lines().toList(), networkPolicy, timeoutSeconds, required, criterionCoverage); }
    public String getId() { return id; }
}
