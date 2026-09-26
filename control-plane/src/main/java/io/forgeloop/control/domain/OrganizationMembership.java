package io.forgeloop.control.domain;

import jakarta.persistence.*;

/** Persisted authorization record; the client never supplies a role or organization in a mutation. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"organization_id", "subject"}))
public class OrganizationMembership {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private String id;
    @ManyToOne(optional = false) private Organization organization;
    @Column(nullable = false) private String subject;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private OperatorRole role;
    protected OrganizationMembership() { }
    public OrganizationMembership(Organization organization, String subject, OperatorRole role) { this.organization = organization; this.subject = subject; this.role = role; }
    public boolean belongsTo(String organizationId, String candidateSubject) { return organization.getId().equals(organizationId) && subject.equals(candidateSubject); }
    public boolean isAdministrator() { return role == OperatorRole.ADMIN; }
    public String getId() { return id; } public String getSubject() { return subject; } public OperatorRole getRole() { return role; }
    public String getOrganizationId() { return organization.getId(); }
}
