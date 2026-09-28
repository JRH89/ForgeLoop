package io.forgeloop.control.domain;

import jakarta.persistence.*;
import java.time.Instant;

/** Persisted authorization record; the client never supplies a role or organization in a mutation. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "subject"}))
public class OrganizationMembership {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private Organization organization;
    @Column(nullable = false) private String subject;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OperatorRole role;
    @Column(name = "github_login") private String githubLogin;
    @Column(name = "accepted_at") private Instant acceptedAt;
    protected OrganizationMembership() { }
    public OrganizationMembership(Organization organization, String subject, OperatorRole role) { this.organization = organization; this.subject = subject; this.role = role; this.acceptedAt = Instant.now(); }
    public static OrganizationMembership invitation(Organization organization, String subject, String githubLogin, OperatorRole role) {
        OrganizationMembership membership = new OrganizationMembership(organization, subject, role);
        membership.githubLogin = githubLogin;
        membership.acceptedAt = null;
        return membership;
    }
    public void accept() { if (acceptedAt == null) acceptedAt = Instant.now(); }
    public boolean belongsTo(String organizationId, String candidateSubject) { return organization.getId().equals(organizationId) && subject.equals(candidateSubject); }
    public boolean isAdministrator() { return role == OperatorRole.ADMIN; }
    public String getId() { return id; } public String getSubject() { return subject; } public OperatorRole getRole() { return role; }
    public String getGithubLogin() { return githubLogin; } public Instant getAcceptedAt() { return acceptedAt; }
    public boolean isAccepted() { return acceptedAt != null; }
    public String getOrganizationId() { return organization.getId(); }
    public String getOrganizationName() { return organization.getName(); }
}
